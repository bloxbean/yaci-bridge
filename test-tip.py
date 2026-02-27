from yaci import YaciBridge, NetworkType

with YaciBridge() as bridge:
    tip = bridge.find_tip("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
    print(f"Tip: slot={tip.slot}, block={tip.block}, hash={tip.hash[:16]}...")
