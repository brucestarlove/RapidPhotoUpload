package com.starscape.rapidupload.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time progress updates.
 * Uses STOMP protocol over WebSocket with SockJS fallback.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final String[] allowedOrigins;
    
    /**
     * Constructor for WebSocket configuration.
     * @param allowedOriginsConfig Comma-separated list of allowed origin patterns (e.g., "http://localhost:3000,https://example.com")
     *                             or "*" to allow all origins. Defaults to "*" if not specified.
     *                             Can also be configured as a YAML list in application.yml.
     */
    public WebSocketConfig(@Value("${app.websocket.allowed-origins:*}") String allowedOriginsConfig) {
        // Handle both single string and comma-separated values
        if (allowedOriginsConfig != null && !allowedOriginsConfig.trim().isEmpty()) {
            this.allowedOrigins = allowedOriginsConfig.split(",");
        } else {
            this.allowedOrigins = new String[]{"*"};
        }
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple broker for topic and queue destinations
        registry.enableSimpleBroker("/topic", "/queue");
        
        // Prefix for client messages
        registry.setApplicationDestinationPrefixes("/app");
        
        // Prefix for user-specific messages
        registry.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}

