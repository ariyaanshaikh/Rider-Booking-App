package com.ariyaan.Demo.config;

import com.ariyaan.Demo.Entity.User;
import com.ariyaan.Demo.Repository.UserRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

/** Password recovery revokes existing WebSocket sessions on every node, not just future HTTP requests. */
@Component
public class SocketSessions {
    private record Identity(String username,int version) {}
    private final Map<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    private final Map<String,Identity> identities=new ConcurrentHashMap<>();
    private final UserRepository users;
    public SocketSessions(UserRepository users) { this.users=users; }
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(),session);super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) throws Exception {
                sessions.remove(session.getId());identities.remove(session.getId());super.afterConnectionClosed(session,status);
            }
        };
    }
    @EventListener
    public void connected(SessionConnectedEvent event) {
        if(event.getUser() instanceof Authentication auth && auth.getPrincipal() instanceof User user)
            identities.put(SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionId(),new Identity(user.getUsername(),user.getCredentialVersion()));
    }
    @Scheduled(fixedDelay=10000,initialDelay=10000)
    public void revoke() {
        Map<String,Optional<User>> accounts=new HashMap<>();
        identities.forEach((id,identity)->{
            try {
                var current=accounts.computeIfAbsent(identity.username(),users::findByUserName);
                if(current.isEmpty() || !current.get().isEnabled() || current.get().getCredentialVersion()!=identity.version()) {
                    var session=sessions.remove(id);identities.remove(id);
                    if(session!=null) session.close(CloseStatus.POLICY_VIOLATION);
                }
            } catch(Exception ex) { /* Recheck after database connectivity returns. */ }
        });
    }
}
