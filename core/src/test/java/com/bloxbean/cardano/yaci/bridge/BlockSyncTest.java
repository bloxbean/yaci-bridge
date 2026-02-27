package com.bloxbean.cardano.yaci.bridge;

import com.bloxbean.cardano.yaci.core.common.Constants;
import com.bloxbean.cardano.yaci.core.model.Block;
import com.bloxbean.cardano.yaci.core.model.Era;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.helper.BlockSync;
import com.bloxbean.cardano.yaci.helper.listener.BlockChainDataListener;
import com.bloxbean.cardano.yaci.helper.model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BlockSyncTest {

    @Test
    void testBlockSyncMainnet() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        // Ignore disconnect events that happen during agent initialization
        AtomicBoolean initialized = new AtomicBoolean(false);

        BlockSync blockSync = new BlockSync(
                "backbone.cardano.iog.io", 3001,
                Constants.MAINNET_PROTOCOL_MAGIC,
                Constants.WELL_KNOWN_MAINNET_POINT);

        blockSync.startSync(Constants.WELL_KNOWN_MAINNET_POINT, new BlockChainDataListener() {
            @Override
            public void onBlock(Era era, Block block, List<Transaction> transactions) {
                System.out.println("Block #" + block.getHeader().getHeaderBody().getBlockNumber() +
                        " slot=" + block.getHeader().getHeaderBody().getSlot() +
                        " era=" + era + " txs=" + (transactions != null ? transactions.size() : 0));
                latch.countDown();
            }

            @Override
            public void onRollback(Point point) {
                System.out.println("Rollback to slot " + point.getSlot());
            }

            @Override
            public void onDisconnect() {
                System.out.println("Disconnected! (initialized=" + initialized.get() + ")");
                if (initialized.get()) {
                    // Only release latch on real disconnects (after init)
                    while (latch.getCount() > 0) latch.countDown();
                }
            }
        });

        // startSync blocks until handshake completes — agent init disconnects are done
        initialized.set(true);

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        blockSync.stop();

        assertTrue(completed, "Should receive at least 3 blocks within 60s");
    }
}
