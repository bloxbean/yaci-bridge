"""BlockSync wrapper — long-running chain sync with listener dispatch."""

import ctypes
import json
from typing import Optional, Union
from yaci._ffi import YaciLib, EVENT_CALLBACK
from yaci.listener import BlockSyncListener
from yaci.models import Point, WELL_KNOWN_POINTS, NetworkType, BlockInfo


class BlockSync:
    """Long-running block sync that delivers blocks via listener callbacks.

    Usage:
        sync = bridge.block_sync("host", 3001, NetworkType.MAINNET)
        sync.add_listener(MyListener())
        sync.start(Point(slot=123, hash="abc..."))
        # ... blocks stream to listener ...
        sync.stop()
    """

    def __init__(self, lib: YaciLib, host: str, port: int,
                 network: Union[NetworkType, int], *,
                 well_known_point: Optional[Point] = None,
                 keep_alive_interval_ms: int = 5000):
        self._lib = lib
        self._host = host
        self._port = port
        self._session_id = None
        self._listeners = []
        self._callback_ref = None  # prevent GC of ctypes callback
        self._keep_alive_interval_ms = keep_alive_interval_ms

        if isinstance(network, NetworkType):
            self._protocol_magic = int(network)
            wk = well_known_point or WELL_KNOWN_POINTS[network]
        else:
            self._protocol_magic = int(network)
            if well_known_point is None:
                raise ValueError("well_known_point is required for custom networks")
            wk = well_known_point

        self._wk_slot = wk.slot
        self._wk_hash = wk.hash

    def add_listener(self, listener: BlockSyncListener):
        """Register a listener for block events."""
        self._listeners.append(listener)

    def remove_listener(self, listener: BlockSyncListener):
        """Remove a registered listener."""
        self._listeners.remove(listener)

    def start(self, from_point: Point):
        """Start syncing from a specific point.

        Creates the native session, registers the callback, and starts sync.
        Events are delivered synchronously on the Yaci network thread.

        Args:
            from_point: The chain point to start syncing from
        """
        self._create_session()
        self._set_keep_alive_interval()
        self._register_callback()
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_start(
            ffi._thread,
            self._session_id,
            from_point.slot,
            ffi._encode(from_point.hash),
        )
        ffi._check(rc)

    def start_from_tip(self):
        """Start syncing from the current chain tip.

        Creates the native session, registers the callback, and starts sync
        from tip. Events are delivered synchronously on the Yaci network thread.
        """
        self._create_session()
        self._set_keep_alive_interval()
        self._register_callback()
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_start_from_tip(
            ffi._thread,
            self._session_id,
        )
        ffi._check(rc)

    def stop(self):
        """Stop syncing and clean up resources."""
        if self._session_id is not None:
            ffi = self._lib
            try:
                ffi._lib.yaci_block_sync_stop(ffi._thread, self._session_id)
            except Exception:
                pass
            try:
                ffi._lib.yaci_block_sync_destroy(ffi._thread, self._session_id)
            except Exception:
                pass
            self._session_id = None
            self._callback_ref = None

    def _create_session(self):
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_create(
            ffi._thread,
            ffi._encode(self._host),
            self._port,
            self._protocol_magic,
            self._wk_slot,
            ffi._encode(self._wk_hash),
        )
        result = ffi._check(rc)
        self._session_id = int(result)

    def _set_keep_alive_interval(self):
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_set_keep_alive_interval(
            ffi._thread, self._session_id, self._keep_alive_interval_ms
        )
        ffi._check(rc)

    def _make_callback(self):
        """Create a ctypes callback that dispatches events to listeners."""
        def _on_event(session_id, event_ptr):
            try:
                raw = ctypes.string_at(event_ptr)
                event = json.loads(raw.decode('utf-8'))
                self._dispatch(event)
            except Exception as e:
                print(f"[yaci] Callback error: {e}", flush=True)
        return EVENT_CALLBACK(_on_event)

    def _register_callback(self):
        """Register the push-based callback with the native session."""
        self._callback_ref = self._make_callback()
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_set_callback(
            ffi._thread, self._session_id, self._callback_ref
        )
        ffi._check(rc)

    def _dispatch(self, event: dict):
        event_type = event.get('type')
        if event_type == 'timeout':
            return

        for listener in self._listeners:
            try:
                if event_type == 'block':
                    block = BlockInfo._from_dict(event)
                    listener.on_block(block.era, block)
                elif event_type == 'rollback':
                    listener.on_rollback(event.get('point'))
                elif event_type == 'disconnect':
                    listener.on_disconnect()
                elif event_type == 'batch_started':
                    listener.on_batch_started()
                elif event_type == 'batch_done':
                    listener.on_batch_done()
                elif event_type == 'no_block_found':
                    listener.on_no_block_found(
                        event.get('from'), event.get('to')
                    )
            except Exception as e:
                print(f"[yaci] Listener error in {event_type}: {e}", flush=True)
