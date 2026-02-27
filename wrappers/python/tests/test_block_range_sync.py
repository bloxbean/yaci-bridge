"""Tests for BlockRangeSync — requires a running Cardano node or public relay."""

import os
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

# Two known mainnet points for range fetch (early Shelley era, verified on-chain)
FROM_POINT = Point(
    slot=16588800,
    hash="078d102d0247463f91eef69fc77f3fbbf120f3118e68cd5e6a493c15446dbf8c"
)
TO_POINT = Point(
    slot=16588941,
    hash="01de569ebc207c3e4d35200e92a5b2e1006d59dacf6fb244c5b2b3a06c43a3f7"
)


class RangeListener(BlockSyncListener):
    """Test listener that collects blocks and signals on batch done."""

    def __init__(self):
        self.blocks = []
        self.batch_started_count = 0
        self.batch_done_count = 0
        self.done_event = threading.Event()

    def on_block(self, era, block):
        self.blocks.append(block)

    def on_batch_started(self):
        self.batch_started_count += 1

    def on_batch_done(self):
        self.batch_done_count += 1
        self.done_event.set()


@pytest.fixture
def bridge():
    b = YaciBridge()
    yield b
    b.close()


def test_block_range_fetch(bridge):
    """Test fetching a small range of blocks."""
    listener = RangeListener()
    range_sync = bridge.block_range_sync(NODE_HOST, NODE_PORT, NETWORK)
    range_sync.add_listener(listener)

    try:
        range_sync.start()
        range_sync.fetch(FROM_POINT, TO_POINT)

        # Wait for batch_done signal
        assert listener.done_event.wait(timeout=30), "Timed out waiting for range fetch"
        assert len(listener.blocks) > 0
        assert listener.batch_started_count >= 1
        assert listener.batch_done_count >= 1

        print(f"Fetched {len(listener.blocks)} blocks in range")
        for b in listener.blocks[:3]:
            assert isinstance(b, BlockInfo)
            print(f"  Block #{b.block_number} at slot {b.slot}")
    finally:
        range_sync.stop()
