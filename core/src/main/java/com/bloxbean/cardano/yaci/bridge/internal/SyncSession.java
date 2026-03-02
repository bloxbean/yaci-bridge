package com.bloxbean.cardano.yaci.bridge.internal;

import com.bloxbean.cardano.yaci.bridge.api.EventCallback;
import com.bloxbean.cardano.yaci.bridge.event.*;
import com.bloxbean.cardano.yaci.bridge.util.NativeString;
import com.bloxbean.cardano.yaci.core.model.Block;
import com.bloxbean.cardano.yaci.core.model.Era;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Tip;
import com.bloxbean.cardano.yaci.helper.BlockSync;
import com.bloxbean.cardano.yaci.helper.listener.BlockChainDataListener;
import com.bloxbean.cardano.yaci.helper.model.Transaction;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncSession {
    private final int id;
    private final BlockSync blockSync;
    private final LinkedBlockingQueue<SyncEvent> eventQueue;
    private volatile boolean started;
    // Suppresses disconnect events fired during agent initialization (before handshake)
    private volatile boolean initialized;
    // Ensures only one DisconnectEvent per connection loss; reset when data flows again
    private final AtomicBoolean disconnectSent = new AtomicBoolean(false);

    // Push-based callback fields
    private EventCallback callback;
    private boolean hasCallback;
    private Thread dispatcherThread;
    private volatile boolean dispatching;

    // Keep-alive fields
    private long keepAliveIntervalMs = 5000;
    private Thread keepAliveThread;
    private volatile boolean keepAliveRunning;

    public SyncSession(int id, String host, int port, long protocolMagic,
                       long wellKnownSlot, String wellKnownHash) {
        this.id = id;
        this.eventQueue = new LinkedBlockingQueue<>();
        Point wellKnownPoint = new Point(wellKnownSlot, wellKnownHash);
        this.blockSync = new BlockSync(host, port, protocolMagic, wellKnownPoint);
        this.started = false;
        this.initialized = false;
    }

    public void setCallback(EventCallback cb) {
        this.callback = cb;
        this.hasCallback = true;
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
        if (hasCallback) {
            startDispatcher();
        }
    }

    public void startFromTip() {
        started = true;
        blockSync.startSyncFromTip(createListener());
        initialized = true;
        startKeepAlive();
        if (hasCallback) {
            startDispatcher();
        }
    }

    public SyncEvent poll(long timeoutMs) throws InterruptedException {
        return eventQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        started = false;
        stopKeepAlive();
        stopDispatcher();
        blockSync.stop();
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

    private void startDispatcher() {
        dispatching = true;
        dispatcherThread = new Thread(() -> {
            while (dispatching) {
                CCharPointer ptr = WordFactory.nullPointer();
                try {
                    SyncEvent event = eventQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (event == null) continue;

                    String json = EventSerializer.serialize(event);
                    ptr = NativeString.toCString(json);
                    callback.invoke(id, ptr);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Log but continue dispatching
                    System.err.println("[yaci] Dispatcher error: " + e.getMessage());
                } finally {
                    if (ptr.isNonNull()) {
                        UnmanagedMemory.free(ptr);
                    }
                }
            }
        }, "yaci-dispatcher-" + id);
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    private void stopDispatcher() {
        dispatching = false;
        if (dispatcherThread != null) {
            try {
                dispatcherThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dispatcherThread = null;
        }
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
                disconnectSent.set(false);
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
                disconnectSent.set(false);
                eventQueue.offer(new RollbackEvent(point.getSlot(), point.getHash()));
            }

            @Override
            public void onDisconnect() {
                // Ignore disconnect events during agent initialization;
                // deduplicate: yaci fires onDisconnect once per internal agent
                if (initialized && disconnectSent.compareAndSet(false, true)) {
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
