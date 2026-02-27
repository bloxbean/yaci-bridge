"""YaciBridge — high-level Python API for Yaci native bridge."""

from yaci._ffi import YaciLib
from yaci.tip_finder import TipFinder
from yaci.block_sync import BlockSync
from yaci.block_range_sync import BlockRangeSync
from yaci.peer_discovery import PeerDiscovery as _PeerDiscovery
from yaci.models import NetworkType, Tip, PeerAddress


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

    def find_tip(self, host: str, port: int, network: NetworkType,
                 timeout_ms: int = 30000) -> Tip:
        """One-shot: connect, query current tip, disconnect.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type (MAINNET, PREPROD, PREVIEW)
            timeout_ms: Timeout in milliseconds

        Returns:
            Tip with slot, hash, and block number
        """
        finder = TipFinder(self._lib, host, port, network)
        return finder.find(timeout_ms)

    def block_sync(self, host: str, port: int, network: NetworkType) -> BlockSync:
        """Create a BlockSync instance for long-running chain sync.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type

        Returns:
            BlockSync instance — call add_listener(), then start()
        """
        return BlockSync(self._lib, host, port, network)

    def discover_peers(self, host: str, port: int, network: NetworkType,
                       request_amount: int = 10,
                       timeout_ms: int = 30000) -> list[PeerAddress]:
        """One-shot: connect, discover peers via PeerSharing, disconnect.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type (MAINNET, PREPROD, PREVIEW)
            request_amount: Number of peers to request (default 10)
            timeout_ms: Timeout in milliseconds

        Returns:
            List of PeerAddress objects
        """
        pd = _PeerDiscovery(self._lib, host, port, network)
        return pd.discover(request_amount, timeout_ms)

    def block_range_sync(self, host: str, port: int,
                         network: NetworkType) -> BlockRangeSync:
        """Create a BlockRangeSync instance for bounded block range fetch.

        Args:
            host: Cardano node hostname
            port: Cardano node port
            network: Network type

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
