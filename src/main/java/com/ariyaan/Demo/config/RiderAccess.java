package com.ariyaan.Demo.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.ariyaan.Demo.Repository.RiderDemoRepository;
import com.ariyaan.Demo.Repository.UserRepository;

/** ADMIN is a customer role. Only explicitly trusted accounts may review KYC. */
@Component("riderAccess")
public class RiderAccess {
    private final UserRepository users;
    private final RiderDemoRepository riders;
    private final Set<String> operators;
    public RiderAccess(UserRepository users, RiderDemoRepository riders,
            @Value("${app.operations.usernames:}") String operators) {
        this.users = users; this.riders = riders;
        this.operators = Arrays.stream(operators.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    public boolean operator(Authentication auth) {
        return auth != null && auth.isAuthenticated() && operators.contains(auth.getName())
                && users.findByUserName(auth.getName()).filter(u -> u.isEnabled()).isPresent();
    }
    public boolean owns(Authentication auth, long riderId) {
        return auth != null && users.findByUserName(auth.getName())
                .filter(u -> u.isEnabled() && u.getRole() == com.ariyaan.Demo.Entity.UserRole.RIDER)
                .flatMap(u -> riders.findByUserId(u.getId())).filter(r -> r.getId() == riderId).isPresent();
    }
}
