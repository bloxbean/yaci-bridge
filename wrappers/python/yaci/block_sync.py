"""BlockSync wrapper — long-running chain sync with listener dispatch."""

import json
import threading
from yaci._ffi import YaciLib
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

    def __init__(self, lib: YaciLib, host: str, port: int, network: NetworkType):
        self._lib = lib
        self._host = host
        self._port = port
        self._network = network
        self._session_id = None
        self._listeners = []
        self._poll_thread = None
        self._running = False

        wk = WELL_KNOWN_POINTS[network]
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

        Creates the native session, starts sync, and launches a background
        polling thread that dispatches events to registered listeners.

        Args:
            from_point: The chain point to start syncing from
        """
        self._create_session()
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_start(
            ffi._thread,
            self._session_id,
            from_point.slot,
            ffi._encode(from_point.hash),
        )
        ffi._check(rc)
        self._start_polling()

    def start_from_tip(self):
        """Start syncing from the current chain tip.

        Creates the native session, starts sync from tip, and launches
        a background polling thread.
        """
        self._create_session()
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_start_from_tip(
            ffi._thread,
            self._session_id,
        )
        ffi._check(rc)
        self._start_polling()

    def stop(self):
        """Stop syncing and clean up resources."""
        self._running = False
        if self._poll_thread:
            self._poll_thread.join(timeout=5)
            self._poll_thread = None

        if self._session_id is not None:
            ffi = self._lib
            try:
                ffi._lib.yaci_block_sync_destroy(ffi._thread, self._session_id)
                # Don't check result — best-effort cleanup
            except Exception:
                pass
            self._session_id = None

    def _create_session(self):
        ffi = self._lib
        rc = ffi._lib.yaci_block_sync_create(
            ffi._thread,
            ffi._encode(self._host),
            self._port,
            int(self._network),
            self._wk_slot,
            ffi._encode(self._wk_hash),
        )
        result = ffi._check(rc)
        self._session_id = int(result)

    def _start_polling(self):
        self._running = True
        self._poll_thread = threading.Thread(
            target=self._poll_loop, daemon=True, name="yaci-block-sync-poll"
        )
        self._poll_thread.start()

    def _poll_loop(self):
        ffi = self._lib
        thread = ffi.attach_thread()
        try:
            while self._running:
                try:
                    rc = ffi._lib.yaci_block_sync_poll(
                        thread, self._session_id, 1000
                    )
                    result = ffi._check(rc, thread)
                    if result:
                        event = json.loads(result)
                        self._dispatch(event)
                except Exception:
                    if self._running:
                        for listener in self._listeners:
                            try:
                                listener.on_disconnect()
                            except Exception:
                                pass
                        break
        finally:
            ffi.detach_thread(thread)

    def _dispatch(self, event: dict):
        event_type = event.get('type')
        if event_type == 'timeout':
            return  # Normal — no event available within poll timeout

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
                pass  # Don't let listener exceptions kill the poll loop
