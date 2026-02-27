package com.bloxbean.cardano.yaci.bridge.api;

import com.bloxbean.cardano.yaci.bridge.ErrorCodes;
import com.bloxbean.cardano.yaci.bridge.util.*;
import com.bloxbean.cardano.yaci.core.protocol.peersharing.messages.PeerAddress;
import com.bloxbean.cardano.yaci.helper.PeerDiscovery;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

            long effectiveTimeout = timeoutMs > 0 ? timeoutMs : 30000;

            // Run discovery on a daemon thread because yaci's Session.handshake()
            // busy-polls with no timeout — if the relay drops the TCP connection
            // during the handshake, that loop blocks forever.  Using a separate
            // thread lets CompletableFuture.get(timeout) reliably time out.
            final PeerDiscovery pd = peerDiscovery;
            CompletableFuture<List<PeerAddress>> future = new CompletableFuture<>();
            Thread worker = new Thread(() -> {
                try {
                    List<PeerAddress> result = pd.discover().block();
                    future.complete(result != null ? result : List.of());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, "peer-discovery");
            worker.setDaemon(true);
            worker.start();

            List<PeerAddress> peers;
            try {
                peers = future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                peerDiscovery.shutdown();
                worker.interrupt();
                ErrorState.set("Peer discovery timed out after " + effectiveTimeout
                        + "ms (node may not support PeerSharing)");
                return ErrorCodes.YACI_ERROR_TIMEOUT;
            } catch (ExecutionException ee) {
                throw (ee.getCause() instanceof Exception)
                        ? (Exception) ee.getCause()
                        : new RuntimeException(ee.getCause());
            }

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
