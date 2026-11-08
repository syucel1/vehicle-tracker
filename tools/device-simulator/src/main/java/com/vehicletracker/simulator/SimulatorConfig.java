package com.vehicletracker.simulator;

import com.vehicletracker.simulator.device.Protocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Command-line options. */
public record SimulatorConfig(
        String host,
        List<Protocol> protocols,
        int devices,
        Duration interval,
        Duration duration,
        String imeiPrefix,
        Map<Protocol, Integer> ports) {

    public static final String USAGE = """
            Usage: java -jar device-simulator.jar [options]

              --host <host>             Gateway host                          (default: localhost)
              --protocol <list|mixed>   gt06, teltonika, h02, osmand or mixed (default: mixed)
                                        comma-separated list allowed, e.g. gt06,teltonika
              --devices <n>             Number of simulated vehicles          (default: 4)
              --interval <seconds>      Seconds between reports per vehicle   (default: 5)
              --duration <seconds>      Stop after this long, 0 = run forever (default: 0)
              --imei-prefix <digits>    First digits of generated IMEIs       (default: 35900000)
              --port <protocol>=<port>  Override a protocol port, repeatable  (e.g. --port gt06=6023)
              --help                    Show this help
            """;

    public int portFor(Protocol protocol) {
        return ports.getOrDefault(protocol, protocol.defaultPort());
    }

    public Protocol protocolFor(int deviceIndex) {
        return protocols.get(deviceIndex % protocols.size());
    }

    /** Builds a valid 15-digit IMEI (14 digits + Luhn check digit) for device {@code index}. */
    public String imeiFor(int index) {
        String number = String.valueOf(index + 1);
        int padding = 14 - imeiPrefix.length() - number.length();
        if (padding < 0) {
            throw new IllegalArgumentException("IMEI prefix too long for " + devices + " devices");
        }
        String body = imeiPrefix + "0".repeat(padding) + number;
        return body + luhnCheckDigit(body);
    }

    static int luhnCheckDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return (10 - sum % 10) % 10;
    }

    public static SimulatorConfig parse(String[] args) {
        String host = "localhost";
        List<Protocol> protocols = List.of(Protocol.values());
        int devices = 4;
        Duration interval = Duration.ofSeconds(5);
        Duration duration = Duration.ZERO;
        String imeiPrefix = "35900000";
        Map<Protocol, Integer> ports = new EnumMap<>(Protocol.class);

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--host" -> host = value(args, ++i, arg);
                case "--protocol" -> protocols = parseProtocols(value(args, ++i, arg));
                case "--devices" -> devices = positiveInt(value(args, ++i, arg), arg);
                case "--interval" -> interval = Duration.ofMillis(
                        Math.round(Double.parseDouble(value(args, ++i, arg)) * 1000));
                case "--duration" -> duration = Duration.ofSeconds(Long.parseLong(value(args, ++i, arg)));
                case "--imei-prefix" -> imeiPrefix = digits(value(args, ++i, arg), arg);
                case "--port" -> {
                    String[] kv = value(args, ++i, arg).split("=", 2);
                    if (kv.length != 2) {
                        throw new IllegalArgumentException("--port expects <protocol>=<port>");
                    }
                    ports.put(Protocol.parse(kv[0]), positiveInt(kv[1], arg));
                }
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (interval.toMillis() < 100) {
            throw new IllegalArgumentException("--interval must be at least 0.1 seconds");
        }
        return new SimulatorConfig(host, protocols, devices, interval, duration, imeiPrefix, ports);
    }

    private static List<Protocol> parseProtocols(String value) {
        if ("mixed".equalsIgnoreCase(value) || "all".equalsIgnoreCase(value)) {
            return List.of(Protocol.values());
        }
        List<Protocol> result = new ArrayList<>();
        for (String part : value.split(",")) {
            result.add(Protocol.parse(part));
        }
        return List.copyOf(result);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    private static int positiveInt(String value, String option) {
        int n = Integer.parseInt(value);
        if (n <= 0) {
            throw new IllegalArgumentException(option + " must be positive");
        }
        return n;
    }

    private static String digits(String value, String option) {
        if (!value.chars().allMatch(Character::isDigit) || value.length() > 13) {
            throw new IllegalArgumentException(option + " must be at most 13 digits");
        }
        return value;
    }
}
