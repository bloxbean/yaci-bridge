# Yaci Bridge - Python Examples

These examples demonstrate how to use the Yaci Python wrapper to interact with Cardano nodes
using Ouroboros mini-protocols.

## Prerequisites

1. **Build the native library**

   ```bash
   ./gradlew :core:nativeCompile
   ```

   This produces the shared library at `core/build/native/nativeCompile/libyaci.dylib` (macOS),
   `libyaci.so` (Linux), or `libyaci.dll` (Windows).

2. **Install the Python wrapper**

   ```bash
   cd wrappers/python
   pip install -e .
   ```

3. **Set the library path**

   Point `YACI_LIB_PATH` to the built native library:

   ```bash
   export YACI_LIB_PATH=core/build/native/nativeCompile/libyaci.dylib
   ```

## Running the Examples

Most examples connect to the Cardano mainnet public relay (`backbone.cardano.iog.io:3001`).
The devnet example connects to a local Yaci DevKit node.
Run from the repository root:

```bash
python3 examples-py/<example>.py
```

## Examples

### `tip_finder.py` - Query the Chain Tip

Connects to a Cardano node and queries the current chain tip (latest block).
Shows both default usage and usage with a custom `NodeClientConfig` for controlling
connection timeout, retry behavior, and reconnection settings.

```bash
python3 examples-py/tip_finder.py
```

**Output:**
```
Tip: slot=152389472, block=11382647, hash=a1b2c3d4e5f6...
Tip (with config): slot=152389472, block=11382647, hash=a1b2c3d4e5f6...
```

**What it demonstrates:**
- Creating a `YaciBridge` instance
- Using the context manager (`with` statement) for automatic cleanup
- Querying the chain tip with `bridge.find_tip()`
- Using `NodeClientConfig` to customize connection timeout, retry, and reconnection behavior

---

### `peer_discovery.py` - Discover Network Peers

Uses the PeerSharing mini-protocol to discover other nodes in the Cardano network.

```bash
python3 examples-py/peer_discovery.py
```

**Output:**
```
1.2.3.4:3001 (IPv4)
[2001:db8::1]:3001 (IPv6)
```

**What it demonstrates:**
- Peer discovery via `bridge.discover_peers()`
- Iterating over `PeerAddress` results

---

### `block_sync.py` - Continuous Block Streaming

Starts a long-running sync from a specific chain point and streams blocks as they arrive.
This is the primary use case for indexers and chain followers. Events are delivered via
a listener pattern - you subclass `BlockSyncListener` and override the callbacks you need.

```bash
python3 examples-py/block_sync.py
```

Press `Ctrl-C` to stop.

**Output:**
```
Block #5086524 slot=16588757 era=Shelley txs=0
Block #5086525 slot=16588777 era=Shelley txs=1
...
```

**What it demonstrates:**
- Creating a `BlockSync` session with `bridge.block_sync()`
- Implementing a `BlockSyncListener` with `on_block`, `on_rollback`, `on_disconnect`
- Starting sync from a specific `Point(slot, hash)`
- Graceful shutdown with `sync.stop()`

**Listener callbacks available:**
| Method | When it fires |
|--------|--------------|
| `on_block(era, block)` | A new block is received. `block` is a `BlockInfo` with slot, hash, block_number, transactions |
| `on_rollback(point)` | Chain rolled back to a previous point (dict with `slot` and `hash`) |
| `on_disconnect()` | Connection to the node was lost |
| `on_batch_started()` | A batch of blocks started arriving |
| `on_batch_done()` | A batch of blocks finished |
| `on_no_block_found(from, to)` | No blocks found in the requested range |

---

### `block_sync_from_tip.py` - Stream New Blocks from the Chain Tip

Similar to `block_sync.py`, but starts from the current chain tip instead of a specific point.
Only new blocks produced after the script starts will be delivered. Useful for monitoring
live chain activity without replaying history.

```bash
python3 examples-py/block_sync_from_tip.py
```

Press `Ctrl-C` to stop.

**Output** (blocks appear as they are minted, roughly every 20 seconds on mainnet):
```
Block #11382648 slot=152389492 era=Conway txs=12
Block #11382649 slot=152389512 era=Conway txs=8
...
```

**What it demonstrates:**
- Starting sync from the tip with `sync.start_from_tip()` (no `Point` needed)
- Receiving only new blocks going forward
- Automatic keep-alive messages (every 5s by default) prevent the node from dropping the idle connection

---

### `block_range_sync.py` - Fetch a Block Range

Fetches a bounded range of blocks between two chain points. Unlike `BlockSync` which follows
the chain continuously, this fetches a specific slice and delivers blocks via the same
listener pattern.

```bash
python3 examples-py/block_range_sync.py
```

Press `Ctrl-C` to stop.

**Output:**
```
-- batch started --
Block #5086524 slot=16588757 era=Shelley txs=0
Block #5086525 slot=16588777 era=Shelley txs=1
...
-- batch done --
```

**What it demonstrates:**
- Creating a `BlockRangeSync` session with `bridge.block_range_sync()`
- Starting the session with `range_sync.start()`
- Fetching a range with `range_sync.fetch(from_point, to_point)`
- Using `on_batch_started` / `on_batch_done` to detect range boundaries

---

### `devnet_sync.py` - Sync from a Custom Devnet

Connects to a local Yaci DevKit devnode (or any custom network) using a custom protocol
magic. Uses `GenesisBlockFinder` to discover the genesis block automatically, then syncs
all blocks from the beginning.

**Prerequisites:** A running Yaci DevKit devnode at `localhost:3001`:
```bash
yaci-cli devnet:start
```

```bash
python3 examples-py/devnet_sync.py
```

Press `Ctrl-C` to stop.

**Output:**
```
Finding genesis block on devnet (magic=42)...
Genesis hash: ...
First block: slot=0 era=Conway hash=e614b94b71e5...
Using well-known point: slot=0, hash=e614b94b71e5...

Starting sync from first block...
Block #1 slot=1 era=Conway txs=0
Block #2 slot=2 era=Conway txs=0
...
Block #14 slot=14 era=Conway txs=1
...
```

**What it demonstrates:**
- Using `bridge.find_genesis()` to discover the genesis/first block on a custom network
- Using `genesis.well_known_point()` as the well-known point for BlockSync
- Passing an `int` protocol magic (42) instead of `NetworkType` for custom networks
- Full devnet sync workflow: discover genesis -> create BlockSync -> start syncing

---

## NodeClientConfig

`NodeClientConfig` lets you control connection behavior for `TipFinder`. Pass it via
the `node_config` parameter:

```python
from yaci import NodeClientConfig

config = NodeClientConfig(
    auto_reconnect=False,          # Don't retry on disconnect (default: True)
    max_retry_attempts=2,          # Max 2 retries (default: unlimited)
    connection_timeout_ms=10000,   # 10s TCP timeout (default: 30000)
    initial_retry_delay_ms=3000,   # 3s delay before retry (default: 8000)
    enable_connection_logging=True, # Log connect/disconnect events (default: True)
)

tip = bridge.find_tip(host, port, network, node_config=config)
```

All fields have sensible defaults — you only need to set the ones you want to change.

---

## Event Delivery Modes

By default, events are delivered via **push-based callbacks** (Java calls directly into Python).
You can switch to the legacy **poll-based** mode by setting:

```bash
export YACI_SYNC_MODE=poll
```

This applies to both `BlockSync` and `BlockRangeSync`. The default `callback` mode has lower
latency since it avoids repeated FFI round-trips.

## Supported Networks

Use `NetworkType` to select the network:

| Network | Enum | Protocol Magic |
|---------|------|---------------|
| Mainnet | `NetworkType.MAINNET` | 764824073 |
| Pre-Production | `NetworkType.PREPROD` | 1 |
| Preview | `NetworkType.PREVIEW` | 2 |
| Custom / DevKit | `int` (e.g., `42`) | Any |

Each built-in network has a well-known point (`WELL_KNOWN_POINTS[network]`). For custom
networks, use `bridge.find_genesis(host, port, magic)` to discover the well-known point
automatically.
