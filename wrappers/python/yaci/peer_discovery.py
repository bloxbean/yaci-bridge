"""PeerDiscovery wrapper — one-shot peer sharing query."""

import json
from yaci._ffi import YaciLib
from yaci.models import PeerAddress, NetworkType


class PeerDiscovery:
    """One-shot peer discovery that connects, queries peers, and disconnects."""

    def __init__(self, lib: YaciLib, host: str, port: int, network: NetworkType):
        self._lib = lib
        self._host = host
        self._port = port
        self._network = network

    def discover(self, request_amount: int = 10, timeout_ms: int = 30000) -> list[PeerAddress]:
        """Discover peers from the connected node via PeerSharing.

        Args:
            request_amount: Number of peers to request (default 10)
            timeout_ms: Timeout in milliseconds (default 30s)

        Returns:
            List of PeerAddress objects
        """
        ffi = self._lib
        rc = ffi._lib.yaci_peer_discovery(
            ffi._thread,
            ffi._encode(self._host),
            self._port,
            int(self._network),
            request_amount,
            timeout_ms,
        )
        result = ffi._check(rc)
        data = json.loads(result)
        return [PeerAddress._from_dict(p) for p in data]
