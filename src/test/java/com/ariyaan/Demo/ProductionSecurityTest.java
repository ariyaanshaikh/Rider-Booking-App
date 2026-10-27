package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.ariyaan.Demo.Entity.*;
import com.ariyaan.Demo.Repository.*;
import com.ariyaan.Demo.config.*;

class ProductionSecurityTest {
    final UserRepository users=mock(UserRepository.class);
    final RiderDemoRepository riders=mock(RiderDemoRepository.class);
    final RideDetailsRepository rides=mock(RideDetailsRepository.class);
    final SocketAccess sockets=new SocketAccess(users,riders,rides);
    final RiderAccess access=new RiderAccess(users,riders,"trusted");
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("alice",null,List.of());
    User user;
    @BeforeEach void setup() {
        user=new User();user.setId(3);user.setUserName("alice");user.setEnabled(true);user.setRole(UserRole.ADMIN);
        when(users.findByUserName("alice")).thenReturn(Optional.of(user));
        var ride=new RideDetailsEntity();ride.setRideId(42);ride.setAdminId(3);ride.setRiderId(9);
        when(rides.findById(42)).thenReturn(Optional.of(ride));
    }
    @Test void customerCanOnlySubscribeToOwnUserAndRideChannels() {
        assertTrue(sockets.allowed(auth,"/topic/user/3"));
        assertFalse(sockets.allowed(auth,"/topic/user/4"));
        assertTrue(sockets.allowed(auth,"/topic/ride/42"));
        assertTrue(sockets.allowed(auth,"/user/queue/ride/42/location"));
        assertFalse(sockets.allowed(auth,"/user/queue/ride/43/location"));
        assertFalse(sockets.allowed(auth,"/topic/rider/9"));
        assertFalse(sockets.allowed(auth,"/topic/**"));
        assertFalse(sockets.allowed(auth,"/queue/anything"));
    }
    @Test void riderCannotSubscribeToOtherRidersOrReadCustomersLocationQueues() {
        user.setRole(UserRole.RIDER);
        var rider=new RiderDemoEntity();rider.setId(9);rider.setUserId(3L);
        when(riders.findByUserId(3L)).thenReturn(Optional.of(rider));
        assertTrue(sockets.allowed(auth,"/topic/rider/9"));
        assertFalse(sockets.allowed(auth,"/topic/rider/10"));
        assertTrue(sockets.allowed(auth,"/user/queue/ride/42/events"));
        assertFalse(sockets.allowed(auth,"/user/queue/ride/42/location"));
        assertTrue(access.owns(auth,9));assertFalse(access.owns(auth,10));
        assertFalse(access.operator(auth));
    }
    @Test void customerRoleDoesNotGrantKycApprovalAndDisabledAccountsLoseAccess() {
        assertFalse(access.operator(auth));assertFalse(access.owns(auth,9));
        when(users.findByUserName("trusted")).thenReturn(Optional.of(user));
        assertTrue(access.operator(new UsernamePasswordAuthenticationToken("trusted",null,List.of())));
        user.setEnabled(false);
        assertFalse(sockets.allowed(auth,"/topic/user/3"));
        assertFalse(access.operator(new UsernamePasswordAuthenticationToken("trusted",null,List.of())));
    }
}
