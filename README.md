# Yaci Bridge (WIP)

A native shared library that exposes [Yaci](https://github.com/bloxbean/yaci) (a Java implementation of Cardano
mini-protocols) to non-Java languages. The library is compiled with GraalVM Native Image and ships with a
ready-to-use **Python wrapper**.

You do **not** need Java or GraalVM at runtime — just build the native library once (see
[Building from Source](#building-from-source)) and start syncing blocks.

## Features

| API | Description |
|-----|-------------|
| **TipFinder** | One-shot query — connect, get current chain tip, disconnect |
| **BlockSync** | Long-running chain sync — streams blocks from any point or the current tip |
| **BlockRangeSync** | Bounded fetch — download a specific range of blocks |
| **GenesisBlockFinder** | One-shot query — discover genesis block and first block of any chain |
| **PeerDiscovery** | One-shot query — discover peer addresses via PeerSharing |

All APIs work over **N2N (node-to-node TCP)** and can connect to any Cardano relay or node,
including custom/dev networks with arbitrary protocol magic.

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

## Quick Start (Python)

### Prerequisites

- Python 3.9+
- `pytest` (for running tests): `pip install pytest`
- The `libyaci` native library (see [Building from Source](#building-from-source) above)

### Install

#### Option 1: pip install (recommended)

Clone this repo and install the Python wrapper with pip:

```bash
git clone https://github.com/bloxbean/yaci-bridge.git
cd yaci-bridge

# Editable install (changes to the wrapper reflect immediately — best for development)
pip install -e wrappers/python

# Or regular install (copies into site-packages)
pip install wrappers/python
```

Then set the path to the native library:

```bash
export YACI_LIB_PATH=/path/to/libyaci.dylib   # macOS
export YACI_LIB_PATH=/path/to/libyaci.so       # Linux
```

Now you can `import yaci` from any project — no `PYTHONPATH` needed.

#### Option 2: Using from another project

If you have a separate project that needs to use yaci-bridge:

```bash
# From your project directory, install yaci pointing to the cloned repo
pip install -e /path/to/yaci-bridge/wrappers/python

# Set the native library path
export YACI_LIB_PATH=/path/to/libyaci.dylib
```

```python
# In your project — just import and use
from yaci import YaciBridge, NetworkType

with YaciBridge() as bridge:
    tip = bridge.find_tip("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
    print(tip)
```

#### Option 3: Manual PYTHONPATH

If you prefer not to use pip, clone this repo and point `PYTHONPATH` at the wrapper:

```bash
git clone https://github.com/bloxbean/yaci-bridge.git
export PYTHONPATH=/path/to/yaci-bridge/wrappers/python
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

### Custom Networks (DevKit / Devnets)

All APIs accept an `int` protocol magic instead of `NetworkType` for custom networks.
For `BlockSync` and `TipFinder`, you also need a well-known point — use `GenesisBlockFinder`
to discover it automatically:

```python
from yaci import YaciBridge, BlockSyncListener

bridge = YaciBridge()

# Step 1: Discover the genesis block
genesis = bridge.find_genesis("localhost", 3001, 42)  # protocol magic 42
wk = genesis.well_known_point()

# Step 2: Use it with BlockSync
sync = bridge.block_sync("localhost", 3001, 42, well_known_point=wk)
sync.add_listener(MyListener())
sync.start(wk)

# BlockRangeSync only needs the protocol magic (no well-known point)
range_sync = bridge.block_range_sync("localhost", 3001, 42)
```

### Connection Configuration (NodeClientConfig)

Use `NodeClientConfig` to control connection behavior for `TipFinder` — timeouts,
auto-reconnect, and retry settings:

```python
from yaci import YaciBridge, NetworkType, NodeClientConfig

config = NodeClientConfig(
    auto_reconnect=False,          # Don't retry on disconnect (default: True)
    max_retry_attempts=2,          # Max 2 retries (default: unlimited)
    connection_timeout_ms=10000,   # 10s TCP timeout (default: 30000)
    initial_retry_delay_ms=3000,   # 3s delay before retry (default: 8000)
    enable_connection_logging=True, # Log connect/disconnect (default: True)
)

with YaciBridge() as bridge:
    tip = bridge.find_tip("backbone.cardano.iog.io", 3001, NetworkType.MAINNET,
                          timeout_ms=15000, node_config=config)
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

For custom networks (e.g., Yaci DevKit with magic `42`), pass the protocol magic as an `int`
and use `GenesisBlockFinder` to discover the well-known point. See [Custom Networks](#custom-networks-devkit--devnets).

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

## Examples

Ready-to-run Python examples are in the [`examples-py/`](examples-py/) folder — see [`examples-py/README.md`](examples-py/README.md) for details.

| Example | Description |
|---------|-------------|
| `tip_finder.py` | Query the chain tip (with NodeClientConfig) |
| `peer_discovery.py` | Discover network peers |
| `block_sync.py` | Continuous block streaming from a point |
| `block_sync_from_tip.py` | Stream new blocks from the current tip |
| `block_range_sync.py` | Fetch a bounded range of blocks |
| `devnet_sync.py` | Custom devnet sync with GenesisBlockFinder |

## Project Structure

```
yaci-bridge/
├── core/                              # Java + GraalVM native image
│   ├── build.gradle                   # Dependencies & native-image config
│   └── src/main/java/.../bridge/
│       ├── YaciBridge.java            # Lifecycle entry points
│       ├── api/
│       │   ├── TipFinderApi.java      # yaci_tip_find, yaci_tip_find_with_config
│       │   ├── BlockSyncApi.java      # yaci_block_sync_*
│       │   ├── BlockRangeSyncApi.java # yaci_block_range_sync_*
│       │   └── GenesisBlockFinderApi.java # yaci_genesis_block_find
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
│       │   ├── genesis_block_finder.py # GenesisBlockFinder (one-shot query)
│       │   ├── models.py              # Point, Tip, GenesisBlock, NodeClientConfig, ...
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
bridge.find_tip(host, port, network,           # Tip query with connection config
                node_config=NodeClientConfig(...))
bridge.find_genesis(host, port, magic)         # Find genesis block (custom networks)
bridge.block_sync(host, port, network)         # Create BlockSync (5s keep-alive)
bridge.block_sync(host, port, magic,           # Custom network with well-known point
                  well_known_point=point)
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

A background keep-alive thread sends periodic messages to prevent the Cardano node from
dropping idle connections (default: every 5 seconds). This is especially useful when syncing
from the tip, where blocks may arrive 20+ seconds apart on mainnet. To customize the interval:

```python
sync = bridge.block_sync(host, port, network, keep_alive_interval_ms=10000)  # 10s
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

# With custom connection config
config = NodeClientConfig(auto_reconnect=False, connection_timeout_ms=10000)
tip = bridge.find_tip(host, port, network, node_config=config)

# Custom network
tip = bridge.find_tip(host, port, 42, well_known_point=genesis.well_known_point())
```

### GenesisBlockFinder

```python
genesis = bridge.find_genesis(host, port, protocol_magic)
print(genesis.genesis_hash)          # Genesis block hash
print(genesis.first_block_slot)      # First block slot
print(genesis.first_block_hash)      # First block hash
print(genesis.first_block_era)       # First block era (e.g., "Byron", "Conway")
wk = genesis.well_known_point()      # Point for use with block_sync()
```

### NodeClientConfig

```python
config = NodeClientConfig(
    auto_reconnect=True,             # Reconnect on disconnect (default: True)
    initial_retry_delay_ms=8000,     # Delay before retry (default: 8000)
    max_retry_attempts=2147483647,   # Max retries (default: unlimited)
    enable_connection_logging=True,  # Log events (default: True)
    connection_timeout_ms=30000,     # TCP timeout (default: 30000)
)
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
| `yaci_tip_find_with_config` | Tip query with NodeClientConfig params |
| `yaci_genesis_block_find` | Find genesis block and first block |
| `yaci_peer_discovery` | One-shot peer discovery |
| `yaci_block_sync_create` | Create a sync session |
| `yaci_block_sync_start` | Start syncing from a point |
| `yaci_block_sync_start_from_tip` | Start syncing from tip |
| `yaci_block_sync_poll` | Poll for next event |
| `yaci_block_sync_set_keep_alive_interval` | Set keep-alive interval (ms) |
| `yaci_block_sync_set_callback` | Set push-based event callback |
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
