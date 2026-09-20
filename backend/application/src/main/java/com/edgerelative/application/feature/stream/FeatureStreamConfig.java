package com.edgerelative.application.feature.stream;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the versioned JSON feature stream. Same-origin dev server only. */
@Configuration
@EnableWebSocket
public class FeatureStreamConfig implements WebSocketConfigurer {

    private final FeatureStreamHandler handler;

    public FeatureStreamConfig(FeatureStreamHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/features")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
