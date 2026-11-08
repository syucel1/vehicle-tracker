package com.vehicletracker.bridge.core;

import com.vehicletracker.bridge.core.TraccarMessages.EventData;
import com.vehicletracker.bridge.core.TraccarMessages.PositionData;
import com.vehicletracker.model.DeviceEvent;
import com.vehicletracker.model.Position;
import com.vehicletracker.model.RejectedMessage;
import com.vehicletracker.model.UnknownDevice;

import java.time.Clock;
import java.util.List;

/**
 * Pure routing logic of the bridge: one inbound gateway message in, zero or more outbound
 * canonical records out. Has no Kafka or Spring dependencies, so it is fully unit-testable.
 */
public final class BridgeProcessor {

    public enum Outcome { ACCEPTED, REJECTED, UNKNOWN_DEVICE, UNKNOWN_DEVICE_SUPPRESSED, IGNORED }

    public record Outbound(String topic, String key, String value) {
    }

    public record Result(Outcome outcome, List<Outbound> records) {
        static Result of(Outcome outcome, Outbound... records) {
            return new Result(outcome, List.of(records));
        }
    }

    public record Topics(String positionsRaw, String positionsRejected, String deviceEvents, String deviceUnknown) {
    }

    private final PayloadCodec codec;
    private final PositionNormalizer positions;
    private final EventNormalizer events;
    private final DeviceRegistry registry;
    private final UnknownDeviceThrottle unknownThrottle;
    private final Topics topics;
    private final Clock clock;

    public BridgeProcessor(PayloadCodec codec, PositionNormalizer positions, EventNormalizer events,
                           DeviceRegistry registry, UnknownDeviceThrottle unknownThrottle, Topics topics,
                           Clock clock) {
        this.codec = codec;
        this.positions = positions;
        this.events = events;
        this.registry = registry;
        this.unknownThrottle = unknownThrottle;
        this.topics = topics;
        this.clock = clock;
    }

    public Result onGatewayPosition(String sourceTopic, String key, String payload) {
        PositionData data;
        try {
            data = codec.read(payload, PositionData.class);
        } catch (RuntimeException e) {
            return reject(sourceTopic, key, null, "unparseable", e.getMessage(), payload);
        }

        PositionNormalizer.Result result = positions.normalize(data);
        if (result instanceof PositionNormalizer.Rejected r) {
            return reject(sourceTopic, key, r.deviceUid(), r.reason(), r.detail(), payload);
        }
        Position position = ((PositionNormalizer.Accepted) result).position();
        String uid = position.deviceUid();

        if (!registry.isAllowed(uid)) {
            if (!unknownThrottle.shouldReport(uid)) {
                return Result.of(Outcome.UNKNOWN_DEVICE_SUPPRESSED);
            }
            UnknownDevice notice = new UnknownDevice(uid, position.protocol(), clock.instant(),
                    position.latitude(), position.longitude(), position.gatewayDeviceId());
            return Result.of(Outcome.UNKNOWN_DEVICE, new Outbound(topics.deviceUnknown(), uid, codec.write(notice)));
        }
        return Result.of(Outcome.ACCEPTED, new Outbound(topics.positionsRaw(), uid, codec.write(position)));
    }

    public Result onGatewayEvent(String sourceTopic, String key, String payload) {
        EventData data;
        try {
            data = codec.read(payload, EventData.class);
        } catch (RuntimeException e) {
            return reject(sourceTopic, key, null, "unparseable", e.getMessage(), payload);
        }
        DeviceEvent event = events.normalize(data);
        if (event == null) {
            return reject(sourceTopic, key, null, "invalid_event", "Missing event type or device", payload);
        }
        if (!registry.isAllowed(event.deviceUid())) {
            return Result.of(Outcome.IGNORED);   // unknown devices are reported from their positions
        }
        return Result.of(Outcome.ACCEPTED, new Outbound(topics.deviceEvents(), event.deviceUid(), codec.write(event)));
    }

    private Result reject(String sourceTopic, String key, String uid, String reason, String detail, String payload) {
        RejectedMessage rejected = new RejectedMessage(sourceTopic, key, uid, reason, detail, payload, clock.instant());
        return Result.of(Outcome.REJECTED,
                new Outbound(topics.positionsRejected(), uid != null ? uid : key, codec.write(rejected)));
    }
}
