package com.vehicletracker.bridge.core;

/** JSON (de)serialisation, abstracted so the core logic stays free of any particular Jackson version. */
public interface PayloadCodec {

    /** @throws RuntimeException if the payload cannot be parsed */
    <T> T read(String json, Class<T> type);

    String write(Object value);
}
