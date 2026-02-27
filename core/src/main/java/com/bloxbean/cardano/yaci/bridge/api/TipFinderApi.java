package com.bloxbean.cardano.yaci.bridge.api;

import com.bloxbean.cardano.yaci.bridge.ErrorCodes;
import com.bloxbean.cardano.yaci.bridge.util.*;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Tip;
import com.bloxbean.cardano.yaci.helper.TipFinder;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TipFinderApi {

    private TipFinderApi() {}

    /**
     * Blocking one-shot: connect, find current tip, disconnect.
     * Result JSON: {"slot": N, "hash": "...", "block": N}
     */
    @CEntryPoint(name = "yaci_tip_find")
    public static int find(IsolateThread thread,
                           CCharPointer hostPtr, int port, long protocolMagic,
                           long wellKnownSlot, CCharPointer wellKnownHashPtr,
                           long timeoutMs) {
        TipFinder tipFinder = null;
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

            Point wellKnownPoint = new Point(wellKnownSlot, wellKnownHash);
            tipFinder = new TipFinder(host, port, wellKnownPoint, protocolMagic);

            Duration timeout = Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 30000);
            Tip tip = tipFinder.find().block(timeout);

            if (tip == null) {
                ErrorState.set("Tip finder returned null");
                return ErrorCodes.YACI_ERROR_TIMEOUT;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("slot", tip.getPoint().getSlot());
            result.put("hash", tip.getPoint().getHash());
            result.put("block", tip.getBlock());

            ResultState.set(JsonHelper.toJson(result));
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("timeout")) {
                ErrorState.set("Tip finder timed out: " + msg);
                return ErrorCodes.YACI_ERROR_TIMEOUT;
            }
            ErrorState.set("Tip finder error: " + (msg != null ? msg : e.getClass().getName()));
            return ErrorCodes.YACI_ERROR_CONNECTION;
        } finally {
            if (tipFinder != null) {
                try {
                    tipFinder.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
