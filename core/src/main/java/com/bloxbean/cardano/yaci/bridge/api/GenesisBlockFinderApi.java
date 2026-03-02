package com.bloxbean.cardano.yaci.bridge.api;

import com.bloxbean.cardano.yaci.bridge.ErrorCodes;
import com.bloxbean.cardano.yaci.bridge.util.*;
import com.bloxbean.cardano.yaci.helper.GenesisBlockFinder;
import com.bloxbean.cardano.yaci.helper.model.StartPoint;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class GenesisBlockFinderApi {

    private GenesisBlockFinderApi() {}

    /**
     * Blocking one-shot: connect, find genesis block and first block, disconnect.
     * Result JSON: {"genesisHash": "...", "firstBlockSlot": N, "firstBlockHash": "...", "firstBlockEra": "..."}
     */
    @CEntryPoint(name = "yaci_genesis_block_find")
    public static int find(IsolateThread thread,
                           CCharPointer hostPtr, int port, long protocolMagic) {
        try {
            String host = NativeString.toJavaString(hostPtr);

            if (host == null || host.isEmpty()) {
                ErrorState.set("Host is required");
                return ErrorCodes.YACI_ERROR_INVALID_ARGUMENT;
            }

            GenesisBlockFinder finder = new GenesisBlockFinder(host, port, protocolMagic);
            Optional<StartPoint> result = finder.getGenesisAndFirstBlock();

            if (result.isEmpty()) {
                ErrorState.set("Genesis block not found (timed out)");
                return ErrorCodes.YACI_ERROR_TIMEOUT;
            }

            StartPoint sp = result.get();
            Map<String, Object> json = new LinkedHashMap<>();
            // genesisHash may be null for devnets (Conway block 0 has no prevHash)
            json.put("genesisHash", sp.getGenesisHash() != null ? sp.getGenesisHash() : "");
            json.put("firstBlockSlot", sp.getFirstBlock().getSlot());
            json.put("firstBlockHash", sp.getFirstBlock().getHash());
            json.put("firstBlockEra", sp.getFirstBlockEra().name());

            ResultState.set(JsonHelper.toJson(json));
            return ErrorCodes.YACI_SUCCESS;
        } catch (Exception e) {
            String msg = e.getMessage();
            ErrorState.set("Genesis block finder error: " + (msg != null ? msg : e.getClass().getName()));
            return ErrorCodes.YACI_ERROR_CONNECTION;
        }
    }
}
