"""Tests for TipFinder — requires a running Cardano node or public relay."""

import os
import pytest
from yaci import YaciBridge, NetworkType

# Skip if no lib available
pytestmark = pytest.mark.skipif(
    not os.environ.get('YACI_LIB_PATH'),
    reason="YACI_LIB_PATH not set — native lib not available"
)

# Test relay — use env vars or defaults
NODE_HOST = os.environ.get('CARDANO_NODE_HOST', 'backbone.cardano.iog.io')
NODE_PORT = int(os.environ.get('CARDANO_NODE_PORT', '3001'))
NETWORK = NetworkType.MAINNET


@pytest.fixture
def bridge():
    b = YaciBridge()
    yield b
    b.close()


def test_find_tip(bridge):
    """Test that we can find the current chain tip."""
    tip = bridge.find_tip(NODE_HOST, NODE_PORT, NETWORK, timeout_ms=30000)
    assert tip.slot > 0
    assert tip.hash is not None and len(tip.hash) > 0
    assert tip.block > 0
    print(f"Tip: slot={tip.slot}, hash={tip.hash[:16]}..., block={tip.block}")


def test_find_tip_preprod():
    """Test tip finding on preprod network."""
    host = os.environ.get('PREPROD_NODE_HOST')
    if not host:
        pytest.skip("PREPROD_NODE_HOST not set")
    port = int(os.environ.get('PREPROD_NODE_PORT', '3001'))

    with YaciBridge() as bridge:
        tip = bridge.find_tip(host, port, NetworkType.PREPROD)
        assert tip.slot > 0
        assert tip.block > 0
