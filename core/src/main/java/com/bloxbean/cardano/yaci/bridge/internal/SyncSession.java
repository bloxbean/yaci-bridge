package com.bloxbean.cardano.yaci.bridge.internal;

import com.bloxbean.cardano.yaci.bridge.event.*;
import com.bloxbean.cardano.yaci.core.model.Block;
import com.bloxbean.cardano.yaci.core.model.Era;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Tip;
import com.bloxbean.cardano.yaci.helper.BlockSync;
import com.bloxbean.cardano.yaci.helper.listener.BlockChainDataListener;
import com.bloxbean.cardano.yaci.helper.model.Transaction;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SyncSession {
    private final int id;
    private final BlockSync blockSync;
    private final LinkedBlockingQueue<SyncEvent> eventQueue;
    private volatile boolean started;
    // Suppresses disconnect events fired during agent initialization (before handshake)
    private volatile boolean initialized;

    public SyncSession(int id, String host, int port, long protocolMagic,
                       long wellKnownSlot, String wellKnownHash) {
        this.id = id;
        this.eventQueue = new LinkedBlockingQueue<>();
        Point wellKnownPoint = new Point(wellKnownSlot, wellKnownHash);
        this.blockSync = new BlockSync(host, port, protocolMagic, wellKnownPoint);
        this.started = false;
        this.initialized = false;
    }

    public void start(Point fromPoint) {
        started = true;
        // startSync blocks until handshake completes; agent.disconnected() fires during init
        blockSync.startSync(fromPoint, createListener());
        initialized = true;
    }

    public void startFromTip() {
        started = true;
        blockSync.startSyncFromTip(createListener());
        initialized = true;
    }

    public SyncEvent poll(long timeoutMs) throws InterruptedException {
        return eventQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        started = false;
        blockSync.stop();
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isRunning() {
        return blockSync.isRunning();
    }

    private BlockChainDataListener createListener() {
        return new BlockChainDataListener() {
            @Override
            public void onBlock(Era era, Block block, List<Transaction> transactions) {
                long slot = block.getHeader().getHeaderBody().getSlot();
                String hash = block.getHeader().getHeaderBody().getBlockHash();
                long blockNumber = block.getHeader().getHeaderBody().getBlockNumber();
                String blockCbor = block.getCbor();

                List<Transaction> txList = transactions != null ? transactions : Collections.emptyList();
                eventQueue.offer(new BlockEvent(
                        era.name(), slot, hash, blockNumber, blockCbor, txList));
            }

            @Override
            public void onRollback(Point point) {
                eventQueue.offer(new RollbackEvent(point.getSlot(), point.getHash()));
            }

            @Override
            public void onDisconnect() {
                // Ignore disconnect events during agent initialization
                if (initialized) {
                    eventQueue.offer(new DisconnectEvent());
                }
            }

            @Override
            public void batchStarted() {
                eventQueue.offer(new SyncEvent("batch_started"));
            }

            @Override
            public void batchDone() {
                eventQueue.offer(new SyncEvent("batch_done"));
            }

            @Override
            public void noBlockFound(Point from, Point to) {
                eventQueue.offer(new SyncEvent("no_block_found"));
            }
        };
    }
}
