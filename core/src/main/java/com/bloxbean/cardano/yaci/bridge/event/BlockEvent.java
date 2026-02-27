package com.bloxbean.cardano.yaci.bridge.event;

import com.bloxbean.cardano.yaci.helper.model.Transaction;

import java.util.List;

public class BlockEvent extends SyncEvent {
    private final String era;
    private final long slot;
    private final String hash;
    private final long blockNumber;
    private final String blockCbor;
    private final List<Transaction> transactions;

    public BlockEvent(String era, long slot, String hash, long blockNumber,
                      String blockCbor, List<Transaction> transactions) {
        super("block");
        this.era = era;
        this.slot = slot;
        this.hash = hash;
        this.blockNumber = blockNumber;
        this.blockCbor = blockCbor;
        this.transactions = transactions;
    }

    public String getEra() { return era; }
    public long getSlot() { return slot; }
    public String getHash() { return hash; }
    public long getBlockNumber() { return blockNumber; }
    public String getBlockCbor() { return blockCbor; }
    public List<Transaction> getTransactions() { return transactions; }
}
