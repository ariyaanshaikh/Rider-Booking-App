package com.ariyaan.Demo.Controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.ariyaan.Demo.Service.RidePaymentService;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final RidePaymentService payments;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ariyaan.Demo.Service.CheckoutSessions sessions;
    public PaymentController(RidePaymentService payments) { this.payments = payments; }
    public record Verification(String razorpay_order_id, String razorpay_payment_id, String razorpay_signature) {}
    @PostMapping("/rides/{rideId}/checkout-session")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> checkout(@PathVariable int rideId, Authentication auth) {
        return sessions.create(rideId,auth.getName());
    }
    @PostMapping("/checkout/order")
    public Map<String,Object> checkoutOrder(@RequestHeader("X-Checkout-Token") String token) {
        var session=sessions.resolve(token);
        return payments.order(session.rideId(),session.username());
    }
    @PostMapping("/checkout/verify")
    public Map<String,Object> checkoutVerify(@RequestHeader("X-Checkout-Token") String token, @RequestBody Verification input) {
        var session=sessions.resolve(token);
        return payments.verify(session.rideId(),session.username(),input.razorpay_order_id(),input.razorpay_payment_id(),input.razorpay_signature());
    }
    @GetMapping("/checkout/status")
    public Map<String,Object> checkoutStatus(@RequestHeader("X-Checkout-Token") String token) {
        var session=sessions.resolve(token);
        return payments.status(session.rideId(),session.username());
    }

    @PostMapping("/rides/{rideId}/order")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> order(@PathVariable int rideId, Authentication auth) {
        return payments.order(rideId, auth.getName());
    }
    @PostMapping("/rides/{rideId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> verify(@PathVariable int rideId, Authentication auth, @RequestBody Verification input) {
        return payments.verify(rideId, auth.getName(), input.razorpay_order_id(), input.razorpay_payment_id(), input.razorpay_signature());
    }
    @GetMapping("/rides/{rideId}")
    public Map<String,Object> status(@PathVariable int rideId, Authentication auth) {
        return payments.status(rideId, auth.getName());
    }
    @PostMapping("/razorpay/webhook")
    public Map<String,Boolean> webhook(@RequestBody String rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader("x-razorpay-event-id") String eventId) {
        payments.webhook(rawBody, signature, eventId);
        return Map.of("received", true);
    }
}
