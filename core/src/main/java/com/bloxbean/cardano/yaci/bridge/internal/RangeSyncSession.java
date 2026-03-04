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
import com.fasterxml.jackson.core.JsonProcessingException;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.util.Collections;
import java.util.List;

public class RangeSyncSession {
    private final int id;
    private final BlockRangeSync blockRangeSync;
    private volatile boolean started;
    private volatile boolean initialized;

    // Synchronous callback — invoked directly from Yaci's Netty thread
    private EventCallback callback;
    private volatile boolean callbackSet;

    public RangeSyncSession(int id, String host, int port, long protocolMagic) {
        this.id = id;
        this.blockRangeSync = new BlockRangeSync(host, port, protocolMagic);
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

    public void start() {
        started = true;
        blockRangeSync.start(createListener());
        initialized = true;
    }

    public void fetch(Point from, Point to) {
        blockRangeSync.fetch(from, to);
    }

    public void stop() {
        started = false;
        callbackSet = false;
        blockRangeSync.stop();
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isRunning() {
        return blockRangeSync.isRunning();
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

    private BlockChainDataListener createListener() {
        return new BlockChainDataListener() {
            @Override
            public void onBlock(Era era, Block block, List<Transaction> transactions) {
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
                invokeCallback(new RollbackEvent(point.getSlot(), point.getHash()));
            }

            @Override
            public void onDisconnect() {
                // Ignore disconnect events during agent initialization
                if (initialized) {
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
