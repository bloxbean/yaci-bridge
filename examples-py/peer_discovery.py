from yaci import YaciBridge, NetworkType

with YaciBridge() as bridge:
    peers = bridge.discover_peers("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
    for peer in peers:
        print(f"{peer.address}:{peer.port} ({peer.type})")
