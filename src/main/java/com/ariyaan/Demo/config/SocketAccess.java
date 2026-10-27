package com.ariyaan.Demo.config;

import com.ariyaan.Demo.Entity.UserRole;
import com.ariyaan.Demo.Repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class SocketAccess {
    private final UserRepository users;
    private final RiderDemoRepository riders;
    private final RideDetailsRepository rides;
    public SocketAccess(UserRepository users, RiderDemoRepository riders, RideDetailsRepository rides) {
        this.users=users; this.riders=riders; this.rides=rides;
    }
    public boolean allowed(Authentication auth, String destination) {
        if (auth == null || destination == null) return false;
        var user = users.findByUserName(auth.getName()).filter(u -> u.isEnabled()).orElse(null);
        if (user == null) return false;
        if(auth.getPrincipal() instanceof com.ariyaan.Demo.Entity.User connected
                && connected.getCredentialVersion()!=user.getCredentialVersion()) return false;
        if (destination.equals("/topic/user/" + user.getId())) return user.getRole() == UserRole.ADMIN;
        var rider = riders.findByUserId(user.getId()).orElse(null);
        if (destination.matches("/topic/rider/[1-9][0-9]*"))
            return user.getRole() == UserRole.RIDER && rider != null
                    && destination.equals("/topic/rider/" + rider.getId());
        String rideId;
        if (destination.matches("/topic/ride/[1-9][0-9]*")) rideId=destination.substring(12);
        else if (destination.matches("/user/queue/ride/[1-9][0-9]*/(location|events)")) rideId=destination.split("/")[4];
        else return false;
        try {
            var ride = rides.findById(Integer.parseInt(rideId)).orElse(null);
            return ride != null && ((user.getRole()==UserRole.ADMIN && ride.getAdminId()==user.getId())
                    || (user.getRole()==UserRole.RIDER && rider!=null && ride.getRiderId()==rider.getId()
                        && !destination.endsWith("/location")));
        } catch (NumberFormatException ex) { return false; }
    }
}
