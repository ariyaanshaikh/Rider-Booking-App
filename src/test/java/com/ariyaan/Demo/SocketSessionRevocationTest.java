package com.ariyaan.Demo;

import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.*;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import com.ariyaan.Demo.Entity.*;
import com.ariyaan.Demo.Repository.UserRepository;
import com.ariyaan.Demo.config.SocketSessions;

class SocketSessionRevocationTest {
    @Test void resettingCredentialsClosesExistingWebSocketSessions() throws Exception {
        var users=mock(UserRepository.class);var sessions=new SocketSessions(users);
        var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("session-1");
        sessions.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(socket);
        var old=new User();old.setUserName("rider");old.setRole(UserRole.RIDER);old.setEnabled(true);
        var auth=new UsernamePasswordAuthenticationToken(old,null,old.getAuthorities());
        var headers=StompHeaderAccessor.create(StompCommand.CONNECTED);headers.setSessionId("session-1");headers.setUser(auth);
        sessions.connected(new SessionConnectedEvent(this,MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders()),auth));
        var current=new User();current.setEnabled(true);current.setCredentialVersion(1);
        when(users.findByUserName("rider")).thenReturn(Optional.of(current));
        sessions.revoke();verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }
}
