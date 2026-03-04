package com.bloxbean.cardano.yaci.bridge.internal;

import com.bloxbean.cardano.yaci.bridge.api.EventCallback;
import com.bloxbean.cardano.yaci.bridge.event.*;
import com.bloxbean.cardano.yaci.bridge.util.NativeString;
import com.bloxbean.cardano.yaci.core.model.Block;
import com.bloxbean.cardano.yaci.core.model.Era;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.helper.BlockSync;
import com.bloxbean.cardano.yaci.helper.listener.BlockChainDataListener;
import com.bloxbean.cardano.yaci.helper.model.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncSession {
    private final int id;
    private final BlockSync blockSync;
    private volatile boolean started;
    // Suppresses disconnect events fired during agent initialization (before handshake)
    private volatile boolean initialized;
    // Ensures only one DisconnectEvent per connection loss; reset when data flows again
    private final AtomicBoolean disconnectSent = new AtomicBoolean(false);

    // Synchronous callback — invoked directly from Yaci's Netty thread
    private EventCallback callback;
    private volatile boolean callbackSet;

    // Keep-alive fields
    private long keepAliveIntervalMs = 5000;
    private Thread keepAliveThread;
    private volatile boolean keepAliveRunning;

    public SyncSession(int id, String host, int port, long protocolMagic,
                       long wellKnownSlot, String wellKnownHash) {
        this.id = id;
        Point wellKnownPoint = new Point(wellKnownSlot, wellKnownHash);
        this.blockSync = new BlockSync(host, port, protocolMagic, wellKnownPoint);
        this.started = false;
        this.initialized = false;
    }

    public void setCallback(EventCallback cb) {
        this.callback = cb;
        this.callbackSet = true;
    }

    public boolean hasCallback() {
        return callbackSet;
    }

    public void setKeepAliveInterval(long ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("Keep-alive interval must be > 0");
        }
        this.keepAliveIntervalMs = ms;
    }

    public void start(Point fromPoint) {
        started = true;
        // startSync blocks until handshake completes; agent.disconnected() fires during init
        blockSync.startSync(fromPoint, createListener());
        initialized = true;
        startKeepAlive();
    }

    public void startFromTip() {
        started = true;
        blockSync.startSyncFromTip(createListener());
        initialized = true;
        startKeepAlive();
    }

    public void stop() {
        started = false;
        callbackSet = false;
        stopKeepAlive();
        blockSync.stop();
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isRunning() {
        return blockSync.isRunning();
    }

    private void invokeCallback(SyncEvent event) {
        if (!callbackSet) return;

        CCharPointer ptr = WordFactory.nullPointer();
        try {
            String json = EventSerializer.serialize(event);
            ptr = NativeString.toCString(json);
            callback.invoke(id, ptr);
        } catch (JsonProcessingException e) {
            System.err.println("[yaci] Failed to serialize event: " + e.getMessage());
        } finally {
            if (ptr.isNonNull()) {
                UnmanagedMemory.free(ptr);
            }
        }
    }

    private void startKeepAlive() {
        keepAliveRunning = true;
        keepAliveThread = new Thread(() -> {
            Random random = new Random();
            while (keepAliveRunning) {
                try {
                    Thread.sleep(keepAliveIntervalMs);
                    if (keepAliveRunning) {
                        blockSync.sendKeepAliveMessage(random.nextInt(60001));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[yaci] Keep-alive error: " + e.getMessage());
                }
            }
        }, "yaci-keepalive-" + id);
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
    }

    private void stopKeepAlive() {
        keepAliveRunning = false;
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            try {
                keepAliveThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            keepAliveThread = null;
        }
    }

    private BlockChainDataListener createListener() {
        return new BlockChainDataListener() {
            @Override
            public void onBlock(Era era, Block block, List<Transaction> transactions) {
                disconnectSent.set(false);
                long slot = block.getHeader().getHeaderBody().getSlot();
                String hash = block.getHeader().getHeaderBody().getBlockHash();
                long blockNumber = block.getHeader().getHeaderBody().getBlockNumber();
                String blockCbor = block.getCbor();

                List<Transaction> txList = transactions != null ? transactions : Collections.emptyList();
                invokeCallback(new BlockEvent(
                        era.name(), slot, hash, blockNumber, blockCbor, txList));
            }

            @Override
            public void onRollback(Point point) {
                disconnectSent.set(false);
                invokeCallback(new RollbackEvent(point.getSlot(), point.getHash()));
            }

            @Override
            public void onDisconnect() {
                // Ignore disconnect events during agent initialization;
                // deduplicate: yaci fires onDisconnect once per internal agent
                if (initialized && disconnectSent.compareAndSet(false, true)) {
                    invokeCallback(new DisconnectEvent());
                }
            }

            @Override
            public void batchStarted() {
                invokeCallback(new SyncEvent("batch_started"));
            }

            @Override
            public void batchDone() {
                invokeCallback(new SyncEvent("batch_done"));
            }

            @Override
            public void noBlockFound(Point from, Point to) {
                invokeCallback(new SyncEvent("no_block_found"));
            }
        };
    }
}
