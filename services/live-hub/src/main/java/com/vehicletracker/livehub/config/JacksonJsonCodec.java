package com.vehicletracker.livehub.config;

import com.vehicletracker.livehub.core.JsonCodec;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 implementation of {@link JsonCodec}. Thread-safe. */
public class JacksonJsonCodec implements JsonCodec {

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
