package com.vehicletracker.simulator.codec;

import java.util.HexFormat;

public final class Hex {

    private static final HexFormat FORMAT = HexFormat.of();

    private Hex() {
    }

    public static String encode(byte[] bytes) {
        return FORMAT.formatHex(bytes);
    }

    public static byte[] decode(String hex) {
        return FORMAT.parseHex(hex.toLowerCase());
    }
}
