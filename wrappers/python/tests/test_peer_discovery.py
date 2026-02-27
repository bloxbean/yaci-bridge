"""Tests for PeerDiscovery — requires a running Cardano node or public relay."""

import os
import pytest
from yaci import YaciBridge, NetworkType, PeerAddress

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


def test_discover_peers(bridge):
    """Test that we can discover peers from a mainnet relay."""
    peers = bridge.discover_peers(NODE_HOST, NODE_PORT, NETWORK, timeout_ms=30000)
    assert isinstance(peers, list)
    assert len(peers) > 0, "Expected at least one peer"
    for peer in peers:
        assert isinstance(peer, PeerAddress)
        assert peer.type in ("IPv4", "IPv6")
        assert peer.address
        assert peer.port > 0
    print(f"Discovered {len(peers)} peers:")
    for p in peers:
        print(f"  {p.type} {p.address}:{p.port}")
