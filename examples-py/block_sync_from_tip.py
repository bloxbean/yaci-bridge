import time
from yaci import YaciBridge, BlockSyncListener, NetworkType

class MyListener(BlockSyncListener):
    def on_block(self, era, block):
        print(f"Block #{block.block_number} slot={block.slot} era={era} "
              f"txs={len(block.transactions)}")

    def on_rollback(self, point):
        print(f"Rollback to slot {point['slot']}")

    def on_disconnect(self):
        print("Disconnected")

bridge = YaciBridge()
sync = bridge.block_sync("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
sync.add_listener(MyListener())

# Start syncing from the current chain tip — only new blocks from now on
sync.start_from_tip()

try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    print("\nStopping...")
    sync.stop()
    bridge.close()
