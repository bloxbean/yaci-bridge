import time
from yaci import YaciBridge, BlockSyncListener, Point, NetworkType

class MyListener(BlockSyncListener):
    def on_block(self, era, block):
        print(f"Block #{block['blockNumber']} slot={block['slot']} era={era} "
              f"txs={len(block.get('transactions', []))}")

    def on_rollback(self, point):
        print(f"Rollback to slot {point['slot']}")

    def on_disconnect(self):
        print("Disconnected")

bridge = YaciBridge()
sync = bridge.block_sync("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
sync.add_listener(MyListener())

# Start from the well-known Shelley-era point
sync.start(Point(
    slot=16588737,
    hash="4e9bbbb67e3ae262133d94c3da5bffce7b1127fc436e7433b87668dba34c354a"
))

try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    print("\nStopping...")
    sync.stop()
    bridge.close()
