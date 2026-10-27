package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.ariyaan.Demo.config.*;
import com.ariyaan.Demo.Service.CustomUserDetailsService;
import com.ariyaan.Demo.Entity.*;

class SocketInterceptorTest {
    final JwtUtils jwt=new JwtUtils("test-signing-key-at-least-32-bytes-long");
    final CustomUserDetailsService users=mock(CustomUserDetailsService.class);
    final SocketAccess policy=mock(SocketAccess.class);
    final JwtChannelInterceptor interceptor=new JwtChannelInterceptor(jwt,users,policy);
    @Test void anonymousConnectAndClientPublishingAreRejected() {
        var connect=StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(AccessDeniedException.class,()->interceptor.preSend(MessageBuilder.createMessage(new byte[0],connect.getMessageHeaders()),null));
        var send=StompHeaderAccessor.create(StompCommand.SEND);send.setDestination("/topic/user/3");
        assertThrows(AccessDeniedException.class,()->interceptor.preSend(MessageBuilder.createMessage(new byte[0],send.getMessageHeaders()),null));
    }
    @Test void subscriptionRequiresUnexpiredSessionAndOwnership() {
        var subscribe=StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setLeaveMutable(true);
        subscribe.setDestination("/topic/user/3");
        var auth=new UsernamePasswordAuthenticationToken("payer",null,List.of());subscribe.setUser(auth);
        subscribe.setSessionAttributes(new HashMap<>(Map.of("jwtExpiresAt",System.currentTimeMillis()+60000)));
        when(policy.allowed(auth,"/topic/user/3")).thenReturn(true);
        assertDoesNotThrow(()->interceptor.preSend(MessageBuilder.createMessage(new byte[0],subscribe.getMessageHeaders()),null));
        subscribe.setDestination("/topic/user/4");
        assertThrows(AccessDeniedException.class,()->interceptor.preSend(MessageBuilder.createMessage(new byte[0],subscribe.getMessageHeaders()),null));
        subscribe.setDestination("/topic/user/3");subscribe.getSessionAttributes().put("jwtExpiresAt",0L);
        assertThrows(AccessDeniedException.class,()->interceptor.preSend(MessageBuilder.createMessage(new byte[0],subscribe.getMessageHeaders()),null));
    }
}
