package com.vehicletracker.livehub.web;

import com.vehicletracker.livehub.config.LiveHubProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveWebSocketHandler handler;
    private final LiveHubProperties props;

    public WebSocketConfig(LiveWebSocketHandler handler, LiveHubProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/live")
                .setAllowedOriginPatterns(props.getAllowedOrigins().toArray(String[]::new));
    }
}
