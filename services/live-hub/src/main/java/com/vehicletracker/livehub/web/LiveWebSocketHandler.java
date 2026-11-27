package com.vehicletracker.livehub.web;

import com.vehicletracker.livehub.config.LiveHubProperties;
import com.vehicletracker.livehub.core.Authorizer;
import com.vehicletracker.livehub.core.ClientSession;
import com.vehicletracker.livehub.core.JsonCodec;
import com.vehicletracker.livehub.core.LiveHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Clock;

/** Adapts Spring's WebSocket API to {@link ClientSession}. */
@Component
public class LiveWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_LIMIT_BYTES = 8 * 1024 * 1024;

    private final LiveHub hub;
    private final JsonCodec json;
    private final Authorizer authorizer;
    private final Clock clock;
    private final LiveHubProperties props;

    public LiveWebSocketHandler(LiveHub hub, JsonCodec json, Authorizer authorizer, Clock clock,
                                LiveHubProperties props) {
        this.hub = hub;
        this.json = json;
        this.authorizer = authorizer;
        this.clock = clock;
        this.props = props;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Serialises sends from Kafka, timer and request threads; a client that cannot keep up
        // is disconnected (it reconnects and gets a fresh snapshot) instead of buffering forever.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE);
        ClientSession.Sink sink = new ClientSession.Sink() {
            @Override
            public void send(String text) {
                try {
                    safe.sendMessage(new TextMessage(text));
                } catch (IOException | RuntimeException e) {
                    log.debug("Send to {} failed: {}", session.getId(), e.getMessage());
                    close(CloseStatus.SESSION_NOT_RELIABLE.getCode(), "send failed");
                }
            }

            @Override
            public void close(int code, String reason) {
                try {
                    safe.close(new CloseStatus(code, reason));
                } catch (IOException e) {
                    log.debug("Close of {} failed: {}", session.getId(), e.getMessage());
                }
            }

            @Override
            public boolean isOpen() {
                return safe.isOpen();
            }
        };
        hub.register(new ClientSession(session.getId(), sink, json, hub.store(), authorizer, clock,
                props.getAuthTimeout()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientSession client = hub.session(session.getId());
        if (client != null) {
            client.onText(message.getPayload());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error on {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregister(session.getId());
    }
}
