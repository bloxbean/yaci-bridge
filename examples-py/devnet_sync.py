"""Example: Sync blocks from a Yaci DevKit devnet (custom network).

Demonstrates:
- Using GenesisBlockFinder to discover the genesis/first block
- Using the discovered point as well-known point for BlockSync
- Connecting to a custom network with arbitrary protocol magic

Prerequisites:
- A running Yaci DevKit devnode at localhost:3001
  (Start with: yaci-cli devnet:start)
- The devnet uses protocol magic 42 by default

Usage:
    YACI_LIB_PATH=core/build/native/nativeCompile/libyaci.dylib python3 examples-py/devnet_sync.py
"""

import time
from yaci import YaciBridge, BlockSyncListener, Point

DEVNET_HOST = "localhost"
DEVNET_PORT = 3001
DEVNET_MAGIC = 42


class MyListener(BlockSyncListener):
    def on_block(self, era, block):
        print(f"Block #{block.block_number} slot={block.slot} era={era} "
              f"txs={len(block.transactions)}")

    def on_rollback(self, point):
        print(f"Rollback to slot {point['slot']}")

    def on_disconnect(self):
        print("Disconnected from devnet")

    def on_batch_started(self):
        print("-- batch started --")

    def on_batch_done(self):
        print("-- batch done --")


bridge = YaciBridge()

# Step 1: Discover the genesis block (needed as well-known point)
print(f"Finding genesis block on devnet (magic={DEVNET_MAGIC})...")
genesis = bridge.find_genesis(DEVNET_HOST, DEVNET_PORT, DEVNET_MAGIC)
print(f"Genesis hash: {genesis.genesis_hash[:16]}...")
print(f"First block: slot={genesis.first_block_slot} era={genesis.first_block_era} "
      f"hash={genesis.first_block_hash[:16]}...")

wk = genesis.well_known_point()
print(f"Using well-known point: slot={wk.slot}, hash={wk.hash[:16]}...")

# Step 2: Create BlockSync with custom network
sync = bridge.block_sync(DEVNET_HOST, DEVNET_PORT, DEVNET_MAGIC,
                         well_known_point=wk)
sync.add_listener(MyListener())

# Step 3: Start syncing from the first block
print("\nStarting sync from first block...")
sync.start(wk)

try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    print("\nStopping...")
    sync.stop()
    bridge.close()
