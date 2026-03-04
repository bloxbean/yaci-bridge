"""BlockRangeSync wrapper — bounded block range fetch with listener dispatch."""

import ctypes
import json
from typing import Union
from yaci._ffi import YaciLib, EVENT_CALLBACK
from yaci.listener import BlockSyncListener
from yaci.models import Point, NetworkType, BlockInfo


class BlockRangeSync:
    """Bounded block range fetcher that delivers blocks via listener callbacks.

    Usage:
        range_sync = bridge.block_range_sync("host", 3001, NetworkType.MAINNET)
        range_sync.add_listener(MyListener())
        range_sync.start()
        range_sync.fetch(from_point, to_point)
        # ... blocks arrive at listener ...
        range_sync.stop()
    """

    def __init__(self, lib: YaciLib, host: str, port: int,
                 network: Union[NetworkType, int]):
        self._lib = lib
        self._host = host
        self._port = port
        self._protocol_magic = int(network)
        self._session_id = None
        self._listeners = []
        self._callback_ref = None  # prevent GC of ctypes callback

    def add_listener(self, listener: BlockSyncListener):
        """Register a listener for block events."""
        self._listeners.append(listener)

    def remove_listener(self, listener: BlockSyncListener):
        """Remove a registered listener."""
        self._listeners.remove(listener)

    def start(self):
        """Create and start the native session, registering the callback."""
        self._create_session()
        self._register_callback()
        ffi = self._lib
        rc = ffi._lib.yaci_block_range_sync_start(ffi._thread, self._session_id)
        ffi._check(rc)

    def fetch(self, from_point: Point, to_point: Point):
        """Request a range of blocks.

        Args:
            from_point: Start point (inclusive)
            to_point: End point (inclusive)
        """
        if self._session_id is None:
            raise RuntimeError("Session not started. Call start() first.")

        ffi = self._lib
        rc = ffi._lib.yaci_block_range_sync_fetch(
            ffi._thread,
            self._session_id,
            from_point.slot,
            ffi._encode(from_point.hash),
            to_point.slot,
            ffi._encode(to_point.hash),
        )
        ffi._check(rc)

    def stop(self):
        """Stop the session and clean up resources."""
        if self._session_id is not None:
            ffi = self._lib
            try:
                ffi._lib.yaci_block_range_sync_stop(ffi._thread, self._session_id)
            except Exception:
                pass
            try:
                ffi._lib.yaci_block_range_sync_destroy(ffi._thread, self._session_id)
            except Exception:
                pass
            self._session_id = None
            self._callback_ref = None

    def _create_session(self):
        ffi = self._lib
        rc = ffi._lib.yaci_block_range_sync_create(
            ffi._thread,
            ffi._encode(self._host),
            self._port,
            self._protocol_magic,
        )
        result = ffi._check(rc)
        self._session_id = int(result)

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
        rc = ffi._lib.yaci_block_range_sync_set_callback(
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
            except Exception:
                pass
