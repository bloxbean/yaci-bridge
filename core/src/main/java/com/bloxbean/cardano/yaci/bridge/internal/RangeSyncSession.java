package com.bloxbean.cardano.yaci.bridge.internal;

import com.bloxbean.cardano.yaci.bridge.api.EventCallback;
import com.bloxbean.cardano.yaci.bridge.event.*;
import com.bloxbean.cardano.yaci.bridge.util.NativeString;
import com.bloxbean.cardano.yaci.core.model.Block;
import com.bloxbean.cardano.yaci.core.model.Era;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.helper.BlockRangeSync;
import com.bloxbean.cardano.yaci.helper.listener.BlockChainDataListener;
import com.bloxbean.cardano.yaci.helper.model.Transaction;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RangeSyncSession {
    private final int id;
    private final BlockRangeSync blockRangeSync;
    private final LinkedBlockingQueue<SyncEvent> eventQueue;
    private volatile boolean started;
    private volatile boolean initialized;

    // Push-based callback fields
    private EventCallback callback;
    private boolean hasCallback;
    private Thread dispatcherThread;
    private volatile boolean dispatching;

    public RangeSyncSession(int id, String host, int port, long protocolMagic) {
        this.id = id;
        this.eventQueue = new LinkedBlockingQueue<>();
        this.blockRangeSync = new BlockRangeSync(host, port, protocolMagic);
        this.started = false;
        this.initialized = false;
    }

    public void setCallback(EventCallback cb) {
        this.callback = cb;
        this.hasCallback = true;
    }

    public void start() {
        started = true;
        blockRangeSync.start(createListener());
        initialized = true;
        if (hasCallback) {
            startDispatcher();
        }
    }

    public void fetch(Point from, Point to) {
        blockRangeSync.fetch(from, to);
    }

    public SyncEvent poll(long timeoutMs) throws InterruptedException {
        return eventQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        started = false;
        stopDispatcher();
        blockRangeSync.stop();
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
                    System.err.println("[yaci] Dispatcher error: " + e.getMessage());
                } finally {
                    if (ptr.isNonNull()) {
                        UnmanagedMemory.free(ptr);
                    }
                }
            }
        }, "yaci-range-dispatcher-" + id);
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
        return blockRangeSync.isRunning();
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
