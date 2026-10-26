package com.ariyaan.Demo.config;

import com.ariyaan.Demo.Service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Intercepts STOMP CONNECT frames and authenticates the user using JWT.
 *
 * This allows the application to secure WebSocket subscriptions and carry a
 * Principal (Authentication) on subsequent frames.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtChannelInterceptor.class);

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    public JwtChannelInterceptor(JwtUtils jwtUtils, CustomUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Retrieve the accessor associated with the incoming STOMP frame.
        // Wrapping it creates a detached accessor, so setUser would not be
        // visible to Spring's SimpUserRegistry for /user destinations.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Look for Authorization header in native headers (case-insensitive)
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if ((authHeaders == null || authHeaders.isEmpty())) {
                authHeaders = accessor.getNativeHeader("authorization");
            }

            if (authHeaders == null || authHeaders.isEmpty()) {
                throw new AccessDeniedException("A Bearer token is required for the WebSocket connection");
            }

            String bearer = authHeaders.get(0);
            if (bearer == null || !bearer.startsWith("Bearer ")) {
                throw new AccessDeniedException("A valid Bearer token is required for the WebSocket connection");
            }

            try {
                String token = bearer.substring(7);
                String username = jwtUtils.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (!userDetails.isEnabled() || !jwtUtils.validateToken(token, userDetails)) {
                    throw new AccessDeniedException("WebSocket token is invalid or expired");
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                accessor.setUser(auth);
            } catch (AccessDeniedException ex) {
                throw ex;
            } catch (Exception ex) {
                log.debug("WebSocket CONNECT authentication failed: {}", ex.getMessage());
                throw new AccessDeniedException("WebSocket token is invalid or expired");
            }
        }

        return message;
    }

}
