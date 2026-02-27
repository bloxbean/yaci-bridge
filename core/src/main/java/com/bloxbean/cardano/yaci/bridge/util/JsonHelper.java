package com.bloxbean.cardano.yaci.bridge.util;

import com.bloxbean.cardano.yaci.core.model.Amount;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

public final class JsonHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        MAPPER.addMixIn(Amount.class, AmountMixin.class);
    }

    private JsonHelper() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) throws JsonProcessingException {
        return MAPPER.writeValueAsString(obj);
    }

    public static String toJson(Map<String, Object> map) throws JsonProcessingException {
        return MAPPER.writeValueAsString(map);
    }

    private abstract static class AmountMixin {
        @JsonIgnore
        abstract byte[] getAssetNameBytes();
    }
}
