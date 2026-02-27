package com.bloxbean.cardano.yaci.bridge.event;

import java.util.Map;

public class RollbackEvent extends SyncEvent {
    private final Map<String, Object> point;

    public RollbackEvent(long slot, String hash) {
        super("rollback");
        this.point = Map.of("slot", slot, "hash", hash);
    }

    public Map<String, Object> getPoint() { return point; }
}
