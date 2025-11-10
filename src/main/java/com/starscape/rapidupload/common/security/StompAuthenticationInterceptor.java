package com.starscape.rapidupload.common.security;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP channel interceptor that authenticates WebSocket connections
 * by extracting JWT tokens from STOMP CONNECT frame headers.
 * 
 * The frontend sends the token in the STOMP CONNECT frame:
 *   CONNECT
 *   Authorization: Bearer <token>
 *   
 * This interceptor extracts the token, validates it, and sets the
 * authentication in the security context.
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(StompAuthenticationInterceptor.class);
    
    private final JwtTokenProvider tokenProvider;
    
    public StompAuthenticationInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract Authorization header from STOMP CONNECT frame
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    
                    try {
                        if (tokenProvider.isTokenValid(token)) {
                            Claims claims = tokenProvider.validateToken(token);
                            String userId = claims.getSubject();
                            String email = claims.get("email", String.class);
                            String scopesStr = claims.get("scopes", String.class);
                            List<String> scopes = scopesStr != null ? 
                                List.of(scopesStr.split(",")) : List.of();
                            
                            UserPrincipal principal = new UserPrincipal(userId, email, scopes);
                            Authentication authentication = 
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                            
                            // Set authentication in the accessor so it's available in the WebSocket session
                            accessor.setUser(authentication);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            
                            log.debug("Authenticated WebSocket connection for user: {}", userId);
                        } else {
                            log.warn("Invalid JWT token in STOMP CONNECT frame");
                        }
                    } catch (Exception e) {
                        log.error("Failed to authenticate WebSocket connection", e);
                    }
                } else {
                    log.debug("No Bearer token found in STOMP CONNECT headers");
                }
            } else {
                log.debug("No Authorization header found in STOMP CONNECT frame");
            }
        }
        
        return message;
    }
}

