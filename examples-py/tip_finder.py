"""Example: Query the current chain tip with optional NodeClientConfig.

Demonstrates:
- Basic tip query with default settings
- Tip query with custom NodeClientConfig (timeout, retry behavior)

Usage:
    YACI_LIB_PATH=core/build/native/nativeCompile/libyaci.dylib python3 examples-py/tip_finder.py
"""

from yaci import YaciBridge, NetworkType, NodeClientConfig

with YaciBridge() as bridge:
    # 1. Basic usage — default settings
    tip = bridge.find_tip("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
    print(f"Tip: slot={tip.slot}, block={tip.block}, hash={tip.hash[:16]}...")

    # 2. With custom NodeClientConfig — shorter timeout, no auto-reconnect
    config = NodeClientConfig(
        auto_reconnect=False,          # Don't retry on disconnect
        max_retry_attempts=2,          # Max 2 retries (if auto_reconnect were True)
        connection_timeout_ms=10000,   # 10s TCP connection timeout
        initial_retry_delay_ms=3000,   # 3s delay before retry
        enable_connection_logging=True,
    )
    tip = bridge.find_tip("backbone.cardano.iog.io", 3001, NetworkType.MAINNET,
                          timeout_ms=15000, node_config=config)
    print(f"Tip (with config): slot={tip.slot}, block={tip.block}, hash={tip.hash[:16]}...")
