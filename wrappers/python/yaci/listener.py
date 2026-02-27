"""Listener base classes for Yaci event callbacks."""

from yaci.models import BlockInfo


class BlockSyncListener:
    """Base class for BlockSync event listeners.

    Override the methods you're interested in. All methods have default
    no-op implementations so you only need to implement what you need.
    """

    def on_block(self, era: str, block: BlockInfo):
        """Called when a new block is received.

        Args:
            era: The era name (e.g., "BABBAGE", "CONWAY")
            block: Typed BlockInfo with slot, hash, blockNumber, transactions, etc.
        """
        pass

    def on_rollback(self, point: dict):
        """Called when the chain rolls back to a previous point.

        Args:
            point: Dict with keys: slot, hash
        """
        pass

    def on_disconnect(self):
        """Called when the connection to the node is lost."""
        pass

    def on_batch_started(self):
        """Called when a batch of blocks starts arriving."""
        pass

    def on_batch_done(self):
        """Called when a batch of blocks has finished."""
        pass

    def on_no_block_found(self, from_point: dict = None, to_point: dict = None):
        """Called when no blocks were found in a requested range."""
        pass
