package com.bloxbean.cardano.yaci.bridge.event;

public class SyncEvent {
    private final String type;

    public SyncEvent(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
