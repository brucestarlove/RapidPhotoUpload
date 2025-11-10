package com.starscape.rapidupload.common.config;

import com.starscape.rapidupload.common.security.StompAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time progress updates.
 * Uses STOMP protocol over WebSocket with SockJS fallback.
 * 
 * CORS Configuration:
 * - The /ws/info endpoint (used by SockJS for negotiation) must allow the frontend origin
 * - Frontend sends credentials (withCredentials: true), so we must allow specific origins
 * - When credentials are enabled, wildcard "*" origins are not allowed by browsers
 * - setAllowedOriginPatterns supports port wildcards (e.g., http://localhost:*)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final String[] allowedOrigins;
    private final StompAuthenticationInterceptor authenticationInterceptor;
    
    /**
     * Constructor for WebSocket configuration.
     * @param allowedOriginsConfig Comma-separated list of allowed origin patterns (e.g., "http://localhost:3000,https://example.com")
     *                             Defaults to "http://localhost:*,http://127.0.0.1:*" for development.
     *                             Can also be configured as a YAML list in application.yml.
     *                             Note: When frontend sends credentials, wildcard "*" is not allowed.
     * @param authenticationInterceptor Interceptor to authenticate STOMP connections via Bearer tokens
     */
    public WebSocketConfig(
            @Value("${app.websocket.allowed-origins:http://localhost:*,http://127.0.0.1:*}") String allowedOriginsConfig,
            StompAuthenticationInterceptor authenticationInterceptor) {
        // Handle both single string and comma-separated values
        if (allowedOriginsConfig != null && !allowedOriginsConfig.trim().isEmpty() && !allowedOriginsConfig.equals("*")) {
            this.allowedOrigins = allowedOriginsConfig.split(",");
        } else {
            // Default to localhost patterns when "*" is specified (since credentials require specific origins)
            this.allowedOrigins = new String[]{"http://localhost:*", "http://127.0.0.1:*"};
        }
        this.authenticationInterceptor = authenticationInterceptor;
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
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Register authentication interceptor to extract Bearer token from STOMP CONNECT headers
        registration.interceptors(authenticationInterceptor);
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint with SockJS fallback
        // setAllowedOriginPatterns allows wildcards and specific origins
        // Since we're using Bearer tokens (not cookies), credentials are not required
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}

