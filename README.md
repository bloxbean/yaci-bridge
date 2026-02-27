# Yaci Bridge

A native shared library that exposes [Yaci](https://github.com/bloxbean/yaci) (a Java implementation of Cardano
mini-protocols) to non-Java languages. The library is compiled with GraalVM Native Image and ships with a
ready-to-use **Python wrapper**.

You do **not** need Java or GraalVM to use the Python wrapper — just download the pre-built native library for your
platform and start syncing blocks.

## Features

| API | Description |
|-----|-------------|
| **TipFinder** | One-shot query — connect, get current chain tip, disconnect |
| **BlockSync** | Long-running chain sync — streams blocks from any point or the current tip |
| **BlockRangeSync** | Bounded fetch — download a specific range of blocks |
| **PeerDiscovery** | One-shot query — discover peer addresses via PeerSharing |

All four APIs work over **N2N (node-to-node TCP)** and can connect to any Cardano relay or node.

## Quick Start (Python)

### Prerequisites

- Python 3.9+
- `pytest` (for running tests): `pip install pytest`
- The pre-built `libyaci` shared library for your platform, **or** build it from source (see [Building from Source](#building-from-source))

### Install

There is no pip package yet. Clone this repo and point `YACI_LIB_PATH` at the native library:

```bash
git clone https://github.com/bloxbean/yaci-bridge.git
cd yaci-bridge
```

If you have a pre-built library, set the path:

```bash
export YACI_LIB_PATH=/path/to/libyaci.dylib   # macOS
export YACI_LIB_PATH=/path/to/libyaci.so       # Linux
```

### Find the Current Chain Tip

```python
from yaci import YaciBridge, NetworkType

with YaciBridge() as bridge:
    tip = bridge.find_tip("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
    print(f"Tip: slot={tip.slot}, block={tip.block}, hash={tip.hash[:16]}...")
```

### Discover Peers

```python
from yaci import YaciBridge, NetworkType

with YaciBridge() as bridge:
    peers = bridge.discover_peers("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
    for peer in peers:
        print(f"{peer.address}:{peer.port} ({peer.type})")
```

### Sync Blocks with a Listener

```python
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

# Blocks arrive at the listener in a background thread.
# When done:
# sync.stop()
# bridge.close()
```

### Fetch a Range of Blocks

```python
from yaci import YaciBridge, BlockSyncListener, Point, NetworkType

class RangeCollector(BlockSyncListener):
    def __init__(self):
        self.blocks = []

    def on_block(self, era, block):
        self.blocks.append(block)

    def on_batch_done(self):
        print(f"Batch complete — {len(self.blocks)} blocks received")

bridge = YaciBridge()
collector = RangeCollector()

range_sync = bridge.block_range_sync("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
range_sync.add_listener(collector)
range_sync.start()
range_sync.fetch(
    from_point=Point(slot=16588800, hash="078d102d0247463f91eef69fc77f3fbbf120f3118e68cd5e6a493c15446dbf8c"),
    to_point=Point(slot=16588941, hash="01de569ebc207c3e4d35200e92a5b2e1006d59dacf6fb244c5b2b3a06c43a3f7"),
)

# Wait for blocks, then:
# range_sync.stop()
# bridge.close()
```

### Block Data

Each block delivered to `on_block` is a dict containing:

```python
{
    "type": "block",
    "era": "BABBAGE",                  # Era name (BYRON, SHELLEY, ALLEGRA, MARY, ALONZO, BABBAGE, CONWAY)
    "slot": 123456,                    # Slot number
    "hash": "abcdef...",               # Block hash (hex)
    "blockNumber": 789,                # Block height
    "blockCbor": "82...",              # Full block CBOR (hex) — for detailed parsing
    "transactions": [                  # Transaction summaries
        {
            "txHash": "def...",
            "blockNumber": 789,
            "slot": 123456,
            "invalid": False
        }
    ]
}
```

### Supported Networks

```python
from yaci.models import NetworkType

NetworkType.MAINNET   # Cardano mainnet (protocol magic: 764824073)
NetworkType.PREPROD   # Pre-production testnet (protocol magic: 1)
NetworkType.PREVIEW   # Preview testnet (protocol magic: 2)
```

Well-known points for each network are built in — the wrapper handles them automatically.

### Public Relays

| Network | Host | Port |
|---------|------|------|
| Mainnet | `backbone.cardano.iog.io` | 3001 |
| Preprod | `preprod-node.play.dev.cardano.org` | 3001 |
| Preview | `preview-node.play.dev.cardano.org` | 3001 |

## Running Python Tests

Tests connect to public Cardano relays over the internet — no local node required.

```bash
# From the project root
cd wrappers/python

# Set the path to the native library
export YACI_LIB_PATH=/path/to/libyaci.dylib

# Run all tests
python3 -m pytest tests/ -v

# Run a specific test
python3 -m pytest tests/test_tip_finder.py -v -s
python3 -m pytest tests/test_block_sync.py -v -s
python3 -m pytest tests/test_block_range_sync.py -v -s
```

Or use Make from the project root (assumes the library has been built):

```bash
make test-python
```

## Building from Source

Building the native library requires GraalVM with Native Image support.

### Prerequisites

- **GraalVM JDK 25+** with Native Image
- **Gradle** (wrapper included — no separate install needed)

### Install GraalVM

Using [SDKMAN](https://sdkman.io/) (recommended):

```bash
sdk install java 25.0.2-graal
sdk use java 25.0.2-graal
```

Or download directly from [oracle.com/java/technologies/downloads](https://www.oracle.com/java/technologies/downloads/).

Verify your setup:

```bash
java -version
# Should show: java version "25.x.x" ... Oracle GraalVM ...

native-image --version
# Should show: native-image 25.x.x
```

### Build the Native Library

```bash
# Clone the repository
git clone https://github.com/bloxbean/yaci-bridge.git
cd yaci-bridge

# Build the shared library
./gradlew :core:nativeCompile
```

The build takes about 40-60 seconds. Once complete, the shared library can be found at:

```
core/build/native/nativeCompile/libyaci.dylib   # macOS (arm64 / x86_64)
core/build/native/nativeCompile/libyaci.so       # Linux
```

Copy this file wherever you need it, or point `YACI_LIB_PATH` to it:

```bash
export YACI_LIB_PATH=$(pwd)/core/build/native/nativeCompile/libyaci.dylib   # macOS
export YACI_LIB_PATH=$(pwd)/core/build/native/nativeCompile/libyaci.so       # Linux
```

Or use Make:

```bash
make build          # Build native library
make test-python    # Run Python tests
make test-all       # Build + test
make clean          # Clean everything
```

### Run JVM Tests (Java developers)

The JVM tests verify the core Java code works before native compilation:

```bash
./gradlew :core:test
```

These tests also connect to public Cardano relays.

## Project Structure

```
yaci-bridge/
├── core/                              # Java + GraalVM native image
│   ├── build.gradle                   # Dependencies & native-image config
│   └── src/main/java/.../bridge/
│       ├── YaciBridge.java            # Lifecycle entry points
│       ├── api/
│       │   ├── TipFinderApi.java      # yaci_tip_find
│       │   ├── BlockSyncApi.java      # yaci_block_sync_*
│       │   └── BlockRangeSyncApi.java # yaci_block_range_sync_*
│       ├── internal/                  # Session management & event queues
│       └── event/                     # Event types & serialization
├── wrappers/
│   └── python/
│       ├── yaci/                      # Python package
│       │   ├── bridge.py              # YaciBridge (main entry point)
│       │   ├── block_sync.py          # BlockSync with listener dispatch
│       │   ├── block_range_sync.py    # BlockRangeSync with listener dispatch
│       │   ├── tip_finder.py          # TipFinder (one-shot query)
│       │   ├── peer_discovery.py      # PeerDiscovery (one-shot peer sharing)
│       │   ├── listener.py            # BlockSyncListener base class
│       │   ├── models.py              # Point, Tip, PeerAddress, NetworkType
│       │   └── _ffi.py                # Low-level ctypes FFI bindings
│       └── tests/                     # pytest test suite
└── Makefile                           # Convenience targets
```

## How It Works

Yaci is a Java library that implements Cardano's node-to-node mini-protocols using Netty for networking.
Yaci Bridge compiles this into a platform-native shared library using GraalVM Native Image, exposing a C ABI
that any language with FFI support can call.

```
┌──────────────────────────────────────────┐
│         Python / Go / Rust / ...         │  Language wrappers
├──────────────────────────────────────────┤
│          C ABI (shared library)          │  libyaci.dylib / .so
├──────────────────────────────────────────┤
│       Yaci (Java, compiled native)       │  Cardano mini-protocols
├──────────────────────────────────────────┤
│      Netty (event-driven networking)     │  TCP connections
└──────────────────────────────────────────┘
```

For long-running sync operations, the bridge uses an **event queue pattern**:

1. Netty I/O threads receive data and push events onto a `LinkedBlockingQueue`
2. The caller polls events through the C ABI (`yaci_block_sync_poll`)
3. The Python wrapper runs a background thread that polls and dispatches events to user-registered listeners

## Python API Reference

### YaciBridge

The main entry point. Creates the GraalVM isolate and provides factory methods.

```python
bridge = YaciBridge()                          # Uses YACI_LIB_PATH env var
bridge = YaciBridge("/path/to/libyaci.dylib")  # Explicit path

bridge.version()                               # Returns version string
bridge.find_tip(host, port, network)           # One-shot tip query
bridge.block_sync(host, port, network)         # Create BlockSync
bridge.block_range_sync(host, port, network)   # Create BlockRangeSync
bridge.close()                                 # Release resources
```

### BlockSyncListener

Override the callbacks you need:

```python
class BlockSyncListener:
    def on_block(self, era: str, block: dict): ...
    def on_rollback(self, point: dict): ...
    def on_disconnect(self): ...
    def on_batch_started(self): ...
    def on_batch_done(self): ...
    def on_no_block_found(self, from_point, to_point): ...
```

### BlockSync

```python
sync = bridge.block_sync(host, port, network)
sync.add_listener(listener)
sync.start(Point(slot=..., hash="..."))  # Sync from a specific point
# or
sync.start_from_tip()                    # Sync from current tip
sync.stop()                              # Stop and clean up
```

### BlockRangeSync

```python
range_sync = bridge.block_range_sync(host, port, network)
range_sync.add_listener(listener)
range_sync.start()                       # Connect to node
range_sync.fetch(from_point, to_point)   # Request block range
range_sync.stop()                        # Stop and clean up
```

### PeerDiscovery

```python
peers = bridge.discover_peers(host, port, network, request_amount=10, timeout_ms=30000)
for peer in peers:
    print(peer.address, peer.port, peer.type)  # PeerAddress dataclass
```

### TipFinder

```python
tip = bridge.find_tip(host, port, network, timeout_ms=30000)
print(tip.slot, tip.hash, tip.block)
```

## Native C API

The shared library exports these functions for use from any language with C FFI:

| Function | Description |
|----------|-------------|
| `yaci_version` | Get version string |
| `yaci_get_result` | Get last result (JSON string) |
| `yaci_get_last_error` | Get last error message |
| `yaci_free_string` | Free a returned string |
| `yaci_tip_find` | One-shot tip query |
| `yaci_peer_discovery` | One-shot peer discovery |
| `yaci_block_sync_create` | Create a sync session |
| `yaci_block_sync_start` | Start syncing from a point |
| `yaci_block_sync_start_from_tip` | Start syncing from tip |
| `yaci_block_sync_poll` | Poll for next event |
| `yaci_block_sync_stop` | Stop syncing |
| `yaci_block_sync_destroy` | Destroy session |
| `yaci_block_range_sync_create` | Create a range sync session |
| `yaci_block_range_sync_start` | Start the connection |
| `yaci_block_range_sync_fetch` | Request a block range |
| `yaci_block_range_sync_poll` | Poll for next event |
| `yaci_block_range_sync_stop` | Stop the connection |
| `yaci_block_range_sync_destroy` | Destroy session |

All functions follow the pattern: call function -> check return code -> call `yaci_get_result` or `yaci_get_last_error` -> call `yaci_free_string`. See `wrappers/python/yaci/_ffi.py` for a complete example.

## License

MIT License — see [LICENSE](LICENSE) for details.
