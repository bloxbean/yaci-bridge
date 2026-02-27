package com.bloxbean.cardano.yaci.bridge.event;

import com.bloxbean.cardano.yaci.bridge.util.JsonHelper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EventSerializer {

    private EventSerializer() {}

    public static String serialize(SyncEvent event) throws JsonProcessingException {
        return JsonHelper.toJson(event);
    }

    public static String timeoutEvent() throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "timeout");
        return JsonHelper.toJson(map);
    }

    public static String batchStartedEvent() throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "batch_started");
        return JsonHelper.toJson(map);
    }

    public static String batchDoneEvent() throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "batch_done");
        return JsonHelper.toJson(map);
    }

    public static String noBlockFoundEvent(long fromSlot, String fromHash,
                                           long toSlot, String toHash) throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "no_block_found");
        map.put("from", Map.of("slot", fromSlot, "hash", fromHash));
        map.put("to", Map.of("slot", toSlot, "hash", toHash));
        return JsonHelper.toJson(map);
    }
}
