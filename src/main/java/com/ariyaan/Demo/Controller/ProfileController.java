package com.ariyaan.Demo.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ariyaan.Demo.Entity.InvoiceEntity;
import com.ariyaan.Demo.Service.ProfileReadService;

/** Secure read APIs for the currently logged-in user. */
@RestController
@RequestMapping("/api/profile/me")
public class ProfileController {

    private final ProfileReadService profileReadService;

    public ProfileController(ProfileReadService profileReadService) {
        this.profileReadService = profileReadService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        return ResponseEntity.ok(profileReadService.getMyProfile(authentication.getName()));
    }

    @GetMapping("/rides")
    public ResponseEntity<List<Map<String, Object>>> getRides(Authentication authentication) {
        return ResponseEntity.ok(profileReadService.getMyRides(authentication.getName()));
    }

    @GetMapping("/active-ride")
    public ResponseEntity<Map<String, Object>> getActiveRide(Authentication authentication) {
        return ResponseEntity.ok(profileReadService.getMyActiveRide(authentication.getName()));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceEntity>> getInvoices(Authentication authentication) {
        return ResponseEntity.ok(profileReadService.getMyInvoices(authentication.getName()));
    }

    @GetMapping("/rider-location")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<Map<String, Object>> getRiderLocation(Authentication authentication) {
        return ResponseEntity.ok(profileReadService.getMyRiderLocation(authentication.getName()));
    }

    @GetMapping("/verification-details")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<Map<String, Object>> getVerificationDetails(Authentication authentication) {
        return ResponseEntity.ok(profileReadService.getMyVerificationDetails(authentication.getName()));
    }
}
