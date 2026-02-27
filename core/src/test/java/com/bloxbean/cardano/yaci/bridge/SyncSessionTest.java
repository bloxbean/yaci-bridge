package com.bloxbean.cardano.yaci.bridge;

import com.bloxbean.cardano.yaci.bridge.event.BlockEvent;
import com.bloxbean.cardano.yaci.bridge.event.RollbackEvent;
import com.bloxbean.cardano.yaci.bridge.event.SyncEvent;
import com.bloxbean.cardano.yaci.bridge.internal.SyncSession;
import com.bloxbean.cardano.yaci.core.common.Constants;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SyncSession — the plain-Java wrapper around BlockSync
 * that powers the native C API and Python wrapper.
 * Tests the create → start → poll events → stop lifecycle against a live mainnet relay.
 */
class SyncSessionTest {

    @Test
    void testSyncSessionLifecycle() throws Exception {
        SyncSession session = new SyncSession(
                1,
                "backbone.cardano.iog.io", 3001,
                Constants.MAINNET_PROTOCOL_MAGIC,
                Constants.WELL_KNOWN_MAINNET_POINT.getSlot(),
                Constants.WELL_KNOWN_MAINNET_POINT.getHash());
        try {
            // Start syncing from the well-known point
            session.start(Constants.WELL_KNOWN_MAINNET_POINT);
            assertTrue(session.isStarted(), "Session should be started after start()");

            // Poll events — expect rollback first, then blocks
            List<BlockEvent> blocks = new ArrayList<>();
            boolean gotRollback = false;
            long deadline = System.currentTimeMillis() + 60_000;

            while (System.currentTimeMillis() < deadline) {
                SyncEvent event = session.poll(2000);
                if (event == null) continue;

                System.out.println("Event: type=" + event.getType());

                if (event instanceof RollbackEvent rb) {
                    System.out.println("  Rollback to slot=" + rb.getPoint().get("slot"));
                    gotRollback = true;
                } else if (event instanceof BlockEvent blk) {
                    System.out.println("  Block #" + blk.getBlockNumber() +
                            " slot=" + blk.getSlot() +
                            " hash=" + blk.getHash().substring(0, 16) + "...");
                    blocks.add(blk);
                    if (blocks.size() >= 3) break;
                }
            }

            assertTrue(gotRollback, "Should receive a rollback event (initial rollback to well-known point)");
            assertFalse(blocks.isEmpty(), "Should receive at least one block event within 60s");

            // Validate block event fields
            BlockEvent first = blocks.get(0);
            assertTrue(first.getSlot() > 0, "Block slot should be positive");
            assertNotNull(first.getHash(), "Block hash should not be null");
            assertFalse(first.getHash().isEmpty(), "Block hash should not be empty");
            assertTrue(first.getBlockNumber() > 0, "Block number should be positive");
        } finally {
            session.stop();
            assertFalse(session.isStarted(), "Session should not be started after stop()");
        }
    }
}
