package com.vehicletracker.livehub.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.vehicletracker.model.DeviceEvent;
import com.vehicletracker.model.Position;

import java.time.Instant;
import java.util.List;

/**
 * WebSocket protocol, endpoint {@code /ws/live}. All messages are JSON text frames with a {@code type}.
 *
 * <pre>
 * client -> server
 *   {"type":"auth","token":"..."}                        first message, within the auth timeout
 *   {"type":"subscribe","devices":["359..."],"bounds":{"n":..,"s":..,"e":..,"w":..}}
 *                                                       devices and bounds are optional (null = all)
 *   {"type":"bounds","bounds":{...}}                     viewport changed (null = no viewport filter)
 *   {"type":"ping"}
 *
 * server -> client
 *   {"type":"ready","serverTime":"...","subject":"..."}  after a successful auth
 *   {"type":"snapshot","positions":[Position...]}         after subscribe and after each bounds change
 *   {"type":"positions","positions":[Position...]}        live updates, batched; only the newest per device
 *   {"type":"events","events":[DeviceEvent...]}           device events (online/offline, ignition, alarms)
 *   {"type":"pong","serverTime":"..."}
 *   {"type":"error","code":"...","message":"..."}         codes: unauthorized, not_authenticated, bad_request
 * </pre>
 * Position and DeviceEvent are the canonical contracts from {@code libs/tracker-model}.
 */
public final class Messages {

    private Messages() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientMessage(String type, String token, List<String> devices, Bounds bounds) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServerMessage(
            String type,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant serverTime,
            String subject,
            List<Position> positions,
            List<DeviceEvent> events,
            String code,
            String message) {

        static ServerMessage ready(Instant now, String subject) {
            return new ServerMessage("ready", now, subject, null, null, null, null);
        }

        static ServerMessage snapshot(List<Position> positions) {
            return new ServerMessage("snapshot", null, null, positions, null, null, null);
        }

        static ServerMessage positions(List<Position> positions) {
            return new ServerMessage("positions", null, null, positions, null, null, null);
        }

        static ServerMessage events(List<DeviceEvent> events) {
            return new ServerMessage("events", null, null, null, events, null, null);
        }

        static ServerMessage pong(Instant now) {
            return new ServerMessage("pong", now, null, null, null, null, null);
        }

        static ServerMessage error(String code, String message) {
            return new ServerMessage("error", null, null, null, null, code, message);
        }
    }
}
