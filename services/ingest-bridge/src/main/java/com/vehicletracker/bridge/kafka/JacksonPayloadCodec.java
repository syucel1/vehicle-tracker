package com.vehicletracker.bridge.kafka;

import com.vehicletracker.bridge.core.PayloadCodec;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 implementation of {@link PayloadCodec}. Thread-safe. */
public class JacksonPayloadCodec implements PayloadCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Override
    public <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty payload");
        }
        return mapper.readValue(json, type);
    }

    @Override
    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }
}
