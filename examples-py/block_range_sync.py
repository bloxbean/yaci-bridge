import time
from yaci import YaciBridge, BlockSyncListener, Point, NetworkType

class MyListener(BlockSyncListener):
    def on_block(self, era, block):
        print(f"Block #{block.block_number} slot={block.slot} era={era} "
              f"txs={len(block.transactions)}")

    def on_rollback(self, point):
        print(f"Rollback to slot {point['slot']}")

    def on_disconnect(self):
        print("Disconnected")

    def on_batch_started(self):
        print("-- batch started --")

    def on_batch_done(self):
        print("-- batch done --")

bridge = YaciBridge()
range_sync = bridge.block_range_sync("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
range_sync.add_listener(MyListener())
range_sync.start()

# Fetch a small range of blocks from Shelley era
range_sync.fetch(
    from_point=Point(
        slot=16588737,
        hash="4e9bbbb67e3ae262133d94c3da5bffce7b1127fc436e7433b87668dba34c354a"
    ),
    to_point=Point(
        slot=180711318,
        hash="377e5ccf15d7ca24ca5b0ea33e2b38fe8481ef950cd78f029dc5e6e8b92a2a3a"
    ),
)

try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    print("\nStopping...")
    range_sync.stop()
    bridge.close()
