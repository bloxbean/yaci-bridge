package com.bloxbean.cardano.yaci.bridge.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionRegistry {

    private static final AtomicInteger nextId = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, SyncSession> syncSessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, RangeSyncSession> rangeSessions = new ConcurrentHashMap<>();

    private SessionRegistry() {}

    public static int nextId() {
        return nextId.getAndIncrement();
    }

    public static void putSync(int id, SyncSession session) {
        syncSessions.put(id, session);
    }

    public static SyncSession getSync(int id) {
        return syncSessions.get(id);
    }

    public static SyncSession removeSync(int id) {
        return syncSessions.remove(id);
    }

    public static void putRange(int id, RangeSyncSession session) {
        rangeSessions.put(id, session);
    }

    public static RangeSyncSession getRange(int id) {
        return rangeSessions.get(id);
    }

    public static RangeSyncSession removeRange(int id) {
        return rangeSessions.remove(id);
    }
}
