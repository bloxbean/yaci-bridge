package com.bloxbean.cardano.yaci.bridge.api;

import com.bloxbean.cardano.yaci.bridge.ErrorCodes;
import com.bloxbean.cardano.yaci.bridge.internal.RangeSyncSession;
import com.bloxbean.cardano.yaci.bridge.internal.SessionRegistry;
import com.bloxbean.cardano.yaci.bridge.util.*;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

public final class BlockRangeSyncApi {

    private BlockRangeSyncApi() {}

    @CEntryPoint(name = "yaci_block_range_sync_create")
    public static int create(IsolateThread thread,
                             CCharPointer hostPtr, int port, long protocolMagic) {
        ErrorState.clear();
        ResultState.clear();
        try {
            String host = NativeString.toJavaString(hostPtr);
            if (host == null || host.isEmpty()) {
                ErrorState.set("Host is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            int id = SessionRegistry.nextId();
            RangeSyncSession session = new RangeSyncSession(id, host, port, protocolMagic);
            SessionRegistry.putRange(id, session);

            ResultState.set(String.valueOf(id));
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to create BlockRangeSync session: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_range_sync_start")
    public static int start(IsolateThread thread, int sessionId) {
        ErrorState.clear();
        ResultState.clear();
        try {
            RangeSyncSession session = SessionRegistry.getRange(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }
            if (session.isStarted()) {
                ErrorState.set("Session already started: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_ALREADY_STARTED;
            }
            if (!session.hasCallback()) {
                ErrorState.set("Callback must be set before starting session: " + sessionId);
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            session.start();
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to start BlockRangeSync: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_range_sync_fetch")
    public static int fetch(IsolateThread thread, int sessionId,
                            long fromSlot, CCharPointer fromHashPtr,
                            long toSlot, CCharPointer toHashPtr) {
        ErrorState.clear();
        ResultState.clear();
        try {
            RangeSyncSession session = SessionRegistry.getRange(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }
            if (!session.isStarted()) {
                ErrorState.set("Session not started: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_STARTED;
            }

            String fromHash = NativeString.toJavaString(fromHashPtr);
            String toHash = NativeString.toJavaString(toHashPtr);

            if (fromHash == null || fromHash.isEmpty()) {
                ErrorState.set("From hash is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }
            if (toHash == null || toHash.isEmpty()) {
                ErrorState.set("To hash is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            Point from = new Point(fromSlot, fromHash);
            Point to = new Point(toSlot, toHash);
            session.fetch(from, to);
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to fetch block range: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_range_sync_set_callback")
    public static int setCallback(IsolateThread thread, int sessionId,
                                  EventCallback callback) {
        ErrorState.clear();
        ResultState.clear();
        try {
            RangeSyncSession session = SessionRegistry.getRange(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }
            if (session.isStarted()) {
                ErrorState.set("Cannot set callback after session started: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_ALREADY_STARTED;
            }

            if (callback.isNull()) {
                ErrorState.set("Callback is null");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            session.setCallback(callback);
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to set callback: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_range_sync_stop")
    public static int stop(IsolateThread thread, int sessionId) {
        ErrorState.clear();
        ResultState.clear();
        try {
            RangeSyncSession session = SessionRegistry.getRange(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }

            session.stop();
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to stop BlockRangeSync: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }

    @CEntryPoint(name = "yaci_block_range_sync_destroy")
    public static int destroy(IsolateThread thread, int sessionId) {
        ErrorState.clear();
        ResultState.clear();
        try {
            RangeSyncSession session = SessionRegistry.removeRange(sessionId);
            if (session == null) {
                ErrorState.set("Session not found: " + sessionId);
                return ErrorCodes.YACI_ERROR_SESSION_NOT_FOUND;
            }

            if (session.isStarted()) {
                session.stop();
            }
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            ErrorState.set("Failed to destroy BlockRangeSync session: " + e.getMessage());
            return ErrorCodes.YACI_ERROR_GENERAL;
        }
    }
}
