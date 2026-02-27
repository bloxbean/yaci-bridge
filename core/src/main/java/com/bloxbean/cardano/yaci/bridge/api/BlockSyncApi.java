package com.bloxbean.cardano.yaci.bridge.api;

import com.bloxbean.cardano.yaci.bridge.ErrorCodes;
import com.bloxbean.cardano.yaci.bridge.event.EventSerializer;
import com.bloxbean.cardano.yaci.bridge.event.SyncEvent;
import com.bloxbean.cardano.yaci.bridge.internal.SessionRegistry;
import com.bloxbean.cardano.yaci.bridge.internal.SyncSession;
import com.bloxbean.cardano.yaci.bridge.util.*;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

public final class BlockSyncApi {

    private BlockSyncApi() {}

    @CEntryPoint(name = "yaci_block_sync_create")
    public static int create(IsolateThread thread,
                             CCharPointer hostPtr, int port, long protocolMagic,
                             long wellKnownSlot, CCharPointer wellKnownHashPtr) {
        try {
            String host = NativeString.toJavaString(hostPtr);
            String wellKnownHash = NativeString.toJavaString(wellKnownHashPtr);

            if (host == null || host.isEmpty()) {
                ErrorState.set("Host is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }
            if (wellKnownHash == null || wellKnownHash.isEmpty()) {
                ErrorState.set("Well-known hash is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            int id = SessionRegistry.nextId();
            SyncSession session = new SyncSession(id, host, port, protocolMagic,
                    wellKnownSlot, wellKnownHash);
            SessionRegistry.putSync(id, session);

            ResultState.set(String.valueOf(id));
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to create BlockSync session: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_sync_start")
    public static int start(IsolateThread thread, int sessionId,
                            long fromSlot, CCharPointer fromHashPtr) {
        try {
            SyncSession session = SessionRegistry.getSync(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }
            if (session.isStarted()) {
                ErrorState.set("Session already started: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_ALREADY_STARTED;
            }

            String fromHash = NativeString.toJavaString(fromHashPtr);
            if (fromHash == null || fromHash.isEmpty()) {
                ErrorState.set("From hash is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            Point fromPoint = new Point(fromSlot, fromHash);
            session.start(fromPoint);
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to start BlockSync: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_sync_start_from_tip")
    public static int startFromTip(IsolateThread thread, int sessionId) {
        try {
            SyncSession session = SessionRegistry.getSync(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }
            if (session.isStarted()) {
                ErrorState.set("Session already started: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_ALREADY_STARTED;
            }

            session.startFromTip();
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to start BlockSync from tip: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_sync_poll")
    public static int poll(IsolateThread thread, int sessionId, long timeoutMs) {
        try {
            SyncSession session = SessionRegistry.getSync(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }

            SyncEvent event = session.poll(timeoutMs > 0 ? timeoutMs : 1000);
            if (event == null) {
                ResultState.set(EventSerializer.timeoutEvent());
            } else {
                ResultState.set(EventSerializer.serialize(event));
            }
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Poll error: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_sync_stop")
    public static int stop(IsolateThread thread, int sessionId) {
        try {
            SyncSession session = SessionRegistry.getSync(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }

            session.stop();
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to stop BlockSync: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_sync_destroy")
    public static int destroy(IsolateThread thread, int sessionId) {
        try {
            SyncSession session = SessionRegistry.removeSync(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }

            if (session.isStarted()) {
                session.stop();
            }
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to destroy BlockSync session: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }
}
