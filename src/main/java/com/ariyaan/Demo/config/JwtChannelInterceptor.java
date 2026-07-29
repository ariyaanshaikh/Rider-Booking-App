package com.ariyaan.Demo.config;

import com.ariyaan.Demo.Service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Look for Authorization header in native headers (case-insensitive)
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if ((authHeaders == null || authHeaders.isEmpty())) {
                authHeaders = accessor.getNativeHeader("authorization");
            }

            if (authHeaders != null && !authHeaders.isEmpty()) {
                String bearer = authHeaders.get(0);
                if (bearer != null && bearer.startsWith("Bearer ")) {
                    String token = bearer.substring(7);
                    try {
                        String username = jwtUtils.extractUsername(token);
                        if (username != null && accessor.getUser() == null) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            if (jwtUtils.validateToken(token, userDetails)) {
                                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                                accessor.setUser(auth);
                            }
                        }
                    } catch (Exception ex) {
                        // Failed to authenticate websocket CONNECT. We don't reject the frame here
                        // to let other layers decide; logging helps debugging clients.
                        log.debug("WebSocket CONNECT authentication failed: {}", ex.getMessage());
                    }
                }
            }
        }

        return message;
    }

}
