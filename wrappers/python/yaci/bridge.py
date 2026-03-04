"""YaciBridge — high-level Python API for Yaci native bridge."""

from typing import Optional, Union
from yaci._ffi import YaciLib
from yaci.tip_finder import TipFinder
from yaci.block_sync import BlockSync
from yaci.block_range_sync import BlockRangeSync
from yaci.genesis_block_finder import GenesisBlockFinder
from yaci.peer_discovery import PeerDiscovery as _PeerDiscovery
from yaci.models import (
    NetworkType, Tip, PeerAddress, Point, GenesisBlock, NodeClientConfig,
)


class YaciBridge:
    """Main entry point for the Yaci Python bridge.

    Usage:
        bridge = YaciBridge()
        tip = bridge.find_tip("backbone.cardano.iog.io", 3001, NetworkType.MAINNET)
        print(f"Current tip: slot={tip.slot}")
        bridge.close()

    Or as a context manager:
        with YaciBridge() as bridge:
            tip = bridge.find_tip(...)
    """

    def __init__(self, lib_path=None):
        """Initialize the bridge.

        Args:
            lib_path: Path to directory containing libyaci.dylib/so/dll.
                      Defaults to YACI_LIB_PATH env var or ./yaci/lib/
        """
        self._lib = YaciLib(lib_path)

    def version(self) -> str:
        """Get the bridge version string."""
        return self._lib.version()

    def find_tip(self, host: str, port: int,
                 network: Union[NetworkType, int],
                 timeout_ms: int = 30000, *,
                 well_known_point: Optional[Point] = None,
                 node_config: Optional[NodeClientConfig] = None) -> Tip:
        """One-shot: connect, query current tip, disconnect.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type (MAINNET, PREPROD, PREVIEW) or int protocol magic
            timeout_ms: Timeout in milliseconds
            well_known_point: Required for custom networks (int protocol magic)
            node_config: Optional NodeClientConfig for connection tuning

        Returns:
            Tip with slot, hash, and block number
        """
        finder = TipFinder(self._lib, host, port, network,
                           well_known_point=well_known_point,
                           node_config=node_config)
        return finder.find(timeout_ms)

    def find_genesis(self, host: str, port: int,
                     protocol_magic: int) -> GenesisBlock:
        """One-shot: find the genesis block and first block of a chain.

        Useful for custom/dev networks where the well-known point is not
        known in advance. The returned GenesisBlock provides a
        well_known_point() method for use with block_sync().

        Args:
            host: Cardano node hostname
            port: Cardano node port
            protocol_magic: Protocol magic number (e.g., 42 for devkit)

        Returns:
            GenesisBlock with genesis hash, first block info, and
            a well_known_point() convenience method
        """
        finder = GenesisBlockFinder(self._lib, host, port, protocol_magic)
        return finder.find()

    def block_sync(self, host: str, port: int,
                   network: Union[NetworkType, int], *,
                   well_known_point: Optional[Point] = None,
                   keep_alive_interval_ms: int = 5000) -> BlockSync:
        """Create a BlockSync instance for long-running chain sync.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type or int protocol magic for custom networks
            well_known_point: Required for custom networks (int protocol magic)
            keep_alive_interval_ms: Keep-alive message interval in ms (default 5000)

        Returns:
            BlockSync instance — call add_listener(), then start()
        """
        return BlockSync(self._lib, host, port, network,
                         well_known_point=well_known_point,
                         keep_alive_interval_ms=keep_alive_interval_ms)

    def discover_peers(self, host: str, port: int, network: Union[NetworkType, int],
                       request_amount: int = 10,
                       timeout_ms: int = 30000) -> list[PeerAddress]:
        """One-shot: connect, discover peers via PeerSharing, disconnect.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type (MAINNET, PREPROD, PREVIEW) or int protocol magic
            request_amount: Number of peers to request (default 10)
            timeout_ms: Timeout in milliseconds

        Returns:
            List of PeerAddress objects
        """
        pd = _PeerDiscovery(self._lib, host, port, network)
        return pd.discover(request_amount, timeout_ms)

    def block_range_sync(self, host: str, port: int,
                         network: Union[NetworkType, int]) -> BlockRangeSync:
        """Create a BlockRangeSync instance for bounded block range fetch.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type or int protocol magic for custom networks

        Returns:
            BlockRangeSync instance — call add_listener(), start(), then fetch()
        """
        return BlockRangeSync(self._lib, host, port, network)

    def close(self):
        """Shut down the bridge and release resources."""
        if self._lib:
            self._lib.close()
            self._lib = None

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
