package com.bloxbean.cardano.yaci.bridge.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RollbackEvent extends SyncEvent {
    private final Map<String, Object> point;

    public RollbackEvent(long slot, String hash) {
        super("rollback");
        var map = new HashMap<String, Object>();
        map.put("slot", slot);
        if (hash != null) {
            map.put("hash", hash);
        }
        this.point = Collections.unmodifiableMap(map);
    }

    public Map<String, Object> getPoint() { return point; }
}
