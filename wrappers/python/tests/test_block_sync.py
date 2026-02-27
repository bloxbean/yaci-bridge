"""Tests for BlockSync — requires a running Cardano node or public relay."""

import os
import time
import threading
import pytest
from yaci import YaciBridge, BlockSyncListener, Point, NetworkType, BlockInfo

pytestmark = pytest.mark.skipif(
    not os.environ.get('YACI_LIB_PATH'),
    reason="YACI_LIB_PATH not set — native lib not available"
)

NODE_HOST = os.environ.get('CARDANO_NODE_HOST', 'backbone.cardano.iog.io')
NODE_PORT = int(os.environ.get('CARDANO_NODE_PORT', '3001'))
NETWORK = NetworkType.MAINNET

# A known mainnet point (Shelley era start — from Yaci Constants)
KNOWN_POINT = Point(
    slot=16588737,
    hash="4e9bbbb67e3ae262133d94c3da5bffce7b1127fc436e7433b87668dba34c354a"
)


class BlockEventCollector(BlockSyncListener):
    """Listener that collects events for testing."""

    def __init__(self):
        self.blocks = []
        self.rollbacks = []
        self.disconnects = 0
        self.batch_started = 0
        self.batch_done = 0
        self.event = threading.Event()

    def on_block(self, era, block):
        self.blocks.append(block)
        if len(self.blocks) >= 3:
            self.event.set()

    def on_rollback(self, point):
        self.rollbacks.append(point)

    def on_disconnect(self):
        self.disconnects += 1
        self.event.set()

    def on_batch_started(self):
        self.batch_started += 1

    def on_batch_done(self):
        self.batch_done += 1


@pytest.fixture
def bridge():
    b = YaciBridge()
    yield b
    b.close()


def test_block_sync_from_point(bridge):
    """Test syncing blocks from a known point."""
    listener = BlockEventCollector()
    sync = bridge.block_sync(NODE_HOST, NODE_PORT, NETWORK)
    sync.add_listener(listener)

    try:
        sync.start(KNOWN_POINT)

        # Wait for at least 3 blocks (up to 30 seconds)
        assert listener.event.wait(timeout=30), "Timed out waiting for blocks"
        assert len(listener.blocks) >= 3

        block = listener.blocks[0]
        assert isinstance(block, BlockInfo)
        assert block.slot > 0
        assert isinstance(block.hash, str) and len(block.hash) > 0
        assert block.block_number > 0

        # Verify transactions are typed TransactionInfo objects
        if block.transactions:
            tx = block.transactions[0]
            assert isinstance(tx.tx_hash, str) and len(tx.tx_hash) > 0
            assert tx.slot > 0

        print(f"First block: slot={block.slot}, number={block.block_number}")
    finally:
        sync.stop()


def test_block_sync_from_tip(bridge):
    """Test syncing from the current tip."""
    listener = BlockEventCollector()
    sync = bridge.block_sync(NODE_HOST, NODE_PORT, NETWORK)
    sync.add_listener(listener)

    try:
        sync.start_from_tip()
        # Just verify it starts without error and wait briefly
        time.sleep(5)
        # We may or may not get blocks from tip — depends on chain activity
        print(f"Blocks received from tip: {len(listener.blocks)}")
    finally:
        sync.stop()
