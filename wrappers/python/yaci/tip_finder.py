"""TipFinder wrapper — one-shot current tip query."""

import json
from yaci._ffi import YaciLib
from yaci.models import Point, Tip, WELL_KNOWN_POINTS, NetworkType


class TipFinder:
    """One-shot tip finder that connects, queries the current tip, and disconnects."""

    def __init__(self, lib: YaciLib, host: str, port: int, network: NetworkType):
        self._lib = lib
        self._host = host
        self._port = port
        self._network = network
        wk = WELL_KNOWN_POINTS[network]
        self._wk_slot = wk.slot
        self._wk_hash = wk.hash

    def find(self, timeout_ms: int = 30000) -> Tip:
        """Find the current tip of the chain.

        Args:
            timeout_ms: Timeout in milliseconds (default 30s)

        Returns:
            Tip object with slot, hash, and block number
        """
        ffi = self._lib
        rc = ffi._lib.yaci_tip_find(
            ffi._thread,
            ffi._encode(self._host),
            self._port,
            int(self._network),
            self._wk_slot,
            ffi._encode(self._wk_hash),
            timeout_ms,
        )
        result = ffi._check(rc)
        data = json.loads(result)
        return Tip(slot=data['slot'], hash=data['hash'], block=data['block'])
