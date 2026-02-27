package com.bloxbean.cardano.yaci.bridge.api;

import com.bloxbean.cardano.yaci.bridge.ErrorCodes;
import com.bloxbean.cardano.yaci.bridge.util.*;
import com.bloxbean.cardano.yaci.core.protocol.peersharing.messages.PeerAddress;
import com.bloxbean.cardano.yaci.helper.PeerDiscovery;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PeerDiscoveryApi {

    private PeerDiscoveryApi() {}

    /**
     * Blocking one-shot: connect, discover peers via PeerSharing, disconnect.
     * Result JSON: [{"type":"IPv4","address":"1.2.3.4","port":3001}, ...]
     */
    @CEntryPoint(name = "yaci_peer_discovery")
    public static int discover(IsolateThread thread,
                               CCharPointer hostPtr, int port, long protocolMagic,
                               int requestAmount, long timeoutMs) {
        PeerDiscovery peerDiscovery = null;
        try {
            String host = NativeString.toJavaString(hostPtr);

            if (host == null || host.isEmpty()) {
                ErrorState.set("Host is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            peerDiscovery = new PeerDiscovery(host, port, protocolMagic, requestAmount);

            Duration timeout = Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 30000);
            List<PeerAddress> peers = peerDiscovery.discover().block(timeout);

            if (peers == null) {
                peers = List.of();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (PeerAddress pa : peers) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", pa.getType() != null ? pa.getType().name() : "IPv4");
                entry.put("address", pa.getAddress());
                entry.put("port", pa.getPort());
                result.add(entry);
            }

            ResultState.set(JsonHelper.toJson(result));
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("timeout")) {
                ErrorState.set("Peer discovery timed out: " + msg);
                return ErrorCodes.YACI_ERROR_TIMEOUT;
            }
            ErrorState.set("Peer discovery error: " + (msg != null ? msg : e.getClass().getName()));
            return ErrorCodes.YACI_ERROR_CONNECTION;
        } finally {
            if (peerDiscovery != null) {
                try {
                    peerDiscovery.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
