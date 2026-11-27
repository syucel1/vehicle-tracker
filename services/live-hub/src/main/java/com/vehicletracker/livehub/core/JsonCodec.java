package com.vehicletracker.livehub.core;

/** JSON (de)serialisation, kept behind an interface so the core logic does not depend on a Jackson version. */
public interface JsonCodec {

    /** @throws RuntimeException if the text cannot be parsed */
    <T> T read(String json, Class<T> type);

    String write(Object value);
}
