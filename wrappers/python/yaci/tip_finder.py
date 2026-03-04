"""TipFinder wrapper — one-shot current tip query."""

import json
from typing import Optional, Union
from yaci._ffi import YaciLib
from yaci.models import Point, Tip, NodeClientConfig, WELL_KNOWN_POINTS, NetworkType


class TipFinder:
    """One-shot tip finder that connects, queries the current tip, and disconnects."""

    def __init__(self, lib: YaciLib, host: str, port: int,
                 network: Union[NetworkType, int], *,
                 well_known_point: Optional[Point] = None,
                 node_config: Optional[NodeClientConfig] = None):
        self._lib = lib
        self._host = host
        self._port = port
        self._node_config = node_config

        if isinstance(network, NetworkType):
            self._protocol_magic = int(network)
            wk = well_known_point or WELL_KNOWN_POINTS[network]
        else:
            self._protocol_magic = int(network)
            if well_known_point is None:
                raise ValueError("well_known_point is required for custom networks")
            wk = well_known_point

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

        if self._node_config is not None:
            cfg = self._node_config
            rc = ffi._lib.yaci_tip_find_with_config(
                ffi._thread,
                ffi._encode(self._host),
                self._port,
                self._protocol_magic,
                self._wk_slot,
                ffi._encode(self._wk_hash),
                timeout_ms,
                1 if cfg.auto_reconnect else 0,
                cfg.initial_retry_delay_ms,
                cfg.max_retry_attempts,
                1 if cfg.enable_connection_logging else 0,
                cfg.connection_timeout_ms,
            )
        else:
            rc = ffi._lib.yaci_tip_find(
                ffi._thread,
                ffi._encode(self._host),
                self._port,
                self._protocol_magic,
                self._wk_slot,
                ffi._encode(self._wk_hash),
                timeout_ms,
            )

        result = ffi._check(rc)
        data = json.loads(result)
        return Tip(slot=data['slot'], hash=data['hash'], block=data['block'])
