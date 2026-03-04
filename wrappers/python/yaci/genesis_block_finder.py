"""GenesisBlockFinder wrapper — one-shot genesis block discovery."""

import json
from yaci._ffi import YaciLib
from yaci.models import GenesisBlock


class GenesisBlockFinder:
    """One-shot genesis block finder for discovering the first block of a chain.

    Useful for custom/dev networks where the well-known point is not known
    in advance. The returned GenesisBlock provides a well_known_point()
    method for use with BlockSync.

    Usage:
        genesis = bridge.find_genesis("localhost", 3001, 42)
        wk = genesis.well_known_point()
        sync = bridge.block_sync("localhost", 3001, 42, well_known_point=wk)
    """

    def __init__(self, lib: YaciLib, host: str, port: int, protocol_magic: int):
        self._lib = lib
        self._host = host
        self._port = port
        self._protocol_magic = protocol_magic

    def find(self) -> GenesisBlock:
        """Find the genesis block and first block of the chain.

        Returns:
            GenesisBlock with genesis hash, first block info, and
            a well_known_point() convenience method
        """
        ffi = self._lib
        rc = ffi._lib.yaci_genesis_block_find(
            ffi._thread,
            ffi._encode(self._host),
            self._port,
            self._protocol_magic,
        )
        result = ffi._check(rc)
        data = json.loads(result)
        return GenesisBlock(
            genesis_hash=data['genesisHash'],
            first_block_slot=data['firstBlockSlot'],
            first_block_hash=data['firstBlockHash'],
            first_block_era=data['firstBlockEra'],
        )
