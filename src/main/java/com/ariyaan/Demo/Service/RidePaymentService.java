package com.ariyaan.Demo.Service;

import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RidePaymentService {
    private final RidePaymentStore store;
    private final RazorpayClient razorpay;
    private final SimpMessagingTemplate messages;
    private final com.ariyaan.Demo.Repository.RideDetailsRepository rides;
    private final com.ariyaan.Demo.Repository.UserRepository users;
    private final com.ariyaan.Demo.Repository.RiderDemoRepository riders;
    private final ObjectMapper json = new ObjectMapper();
    public RidePaymentService(RidePaymentStore store, RazorpayClient razorpay, SimpMessagingTemplate messages,
            com.ariyaan.Demo.Repository.RideDetailsRepository rides,
            com.ariyaan.Demo.Repository.UserRepository users,
            com.ariyaan.Demo.Repository.RiderDemoRepository riders) {
        this.store=store; this.razorpay=razorpay; this.messages=messages;
        this.rides=rides; this.users=users; this.riders=riders;
    }
    public Map<String,Object> order(int rideId, String username) {
        razorpay.requireEnabled();
        RidePaymentStore.OrderIntent intent = store.beginOrder(rideId, username);
        RidePaymentStore.Payment payment = intent.payment();
        if (payment.orderId() == null) {
            if (intent.created()) {
                JsonNode order = razorpay.post("/orders", Map.of("amount", payment.amountPaise(),
                        "currency", "INR", "receipt", payment.receipt(), "notes", Map.of("ride_id", String.valueOf(rideId))));
                store.saveOrder(rideId, order);
            } else {
                // A timeout could have created the order. Recover by receipt, never blindly create another.
                JsonNode orders = razorpay.get("/orders?receipt=" + payment.receipt() + "&count=100");
                JsonNode match = null;
                for (JsonNode candidate : orders.path("items")) {
                    if (payment.receipt().equals(candidate.path("receipt").asText())) {
                        if (match != null) throw new IllegalStateException("Multiple orders found; finance reconciliation required");
                        match = candidate;
                    }
                }
                if (match == null) throw new IllegalStateException("Order creation unresolved; reconcile with Razorpay before retrying");
                store.saveOrder(rideId, match);
            }
        }
        Map<String,Object> result = status(rideId, username);
        result.put("keyId", razorpay.keyId());
        return result;
    }
    public Map<String,Object> status(int rideId, String username) {
        store.authorize(rideId, username, false);
        RidePaymentStore.Payment payment = store.get(rideId);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("rideId", rideId); result.put("amountPaise", payment.amountPaise());
        result.put("businessPaise", payment.businessPaise()); result.put("riderPaise", payment.riderPaise());
        result.put("currency", "INR"); result.put("orderId", payment.orderId());
        result.put("paymentStatus", payment.status()); result.put("transferStatus", payment.transferStatus());
        result.put("rideStatus", rides.findById(rideId).orElseThrow().getRideStatus());
        return result;
    }
    public Map<String,Object> verify(int rideId, String username, String orderId, String paymentId, String signature) {
        store.authorize(rideId, username, true);
        RidePaymentStore.Payment payment = store.get(rideId);
        if (payment.orderId() == null || !payment.orderId().equals(orderId)
                || paymentId == null || !paymentId.matches("pay_[A-Za-z0-9]+"))
            throw new IllegalArgumentException("Payment does not belong to the stored ride order");
        razorpay.verifyCheckout(payment.orderId(), paymentId, signature);
        JsonNode captured = razorpay.get("/payments/" + paymentId);
        if (!payment.orderId().equals(captured.path("order_id").asText()))
            throw new IllegalArgumentException("Payment order mismatch");
        if (!"captured".equals(captured.path("status").asText()))
            throw new IllegalStateException("Payment is not captured yet; wait for confirmation");
        if (store.captured(captured)) notifyStarted(rideId);
        return status(rideId, username);
    }
    public void webhook(String body, String signature, String eventId) {
        razorpay.verifyWebhook(body, signature);
        if (eventId == null || eventId.isBlank() || eventId.length() > 256)
            throw new IllegalArgumentException("Razorpay event ID is required");
        JsonNode event;
        try { event = json.readTree(body); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid webhook JSON"); }
        if (event == null || !event.isObject() || event.path("event").asText().isBlank())
            throw new IllegalArgumentException("Invalid webhook event");
        String type = event.path("event").asText();
        if (type.length() > 80) throw new IllegalArgumentException("Invalid webhook event");
        final int[] started = {0};
        store.recordWebhook(eventId, type, () -> {
            if ("payment.captured".equals(type) || "order.paid".equals(type)) {
                JsonNode entity = event.path("payload").path("payment").path("entity");
                if (store.captured(entity)) {
                    started[0] = store.rideIdForOrder(entity.path("order_id").asText());
                }
            } else if ("refund.processed".equals(type)) {
                store.refunded(event.path("payload").path("refund").path("entity"));
            } else if ("transfer.processed".equals(type) || "transfer.failed".equals(type)) {
                store.transferEvent(event.path("payload").path("transfer").path("entity"));
            }
        });
        if (started[0] != 0) notifyStarted(started[0]);
    }
    private void notifyStarted(int rideId) {
        var ride = rides.findById(rideId).orElseThrow();
        var payload = Map.of("type", "RIDE_STARTED", "rideId", rideId, "paymentStatus", "CAPTURED");
        messages.convertAndSend("/topic/user/"+ride.getAdminId(),payload);
        messages.convertAndSend("/topic/rider/"+ride.getRiderId(),payload);
        users.findById((long)ride.getAdminId()).ifPresent(user -> messages.convertAndSendToUser(
                user.getUserName(), "/queue/ride/" + rideId + "/events", payload));
        riders.findById((long)ride.getRiderId()).flatMap(rider -> users.findById(rider.getUserId()))
                .ifPresent(user -> messages.convertAndSendToUser(user.getUserName(), "/queue/ride/" + rideId + "/events", payload));
    }
    /** Disabled until Route approval, credentials and trusted linked-account mappings are configured. */
    @Scheduled(fixedDelayString="${razorpay.transfer-poll-ms:60000}")
    public void processTransfers() {
        if (!razorpay.routeEnabled()) return;
        for (var candidate : store.transferable()) {
            try {
                if (candidate.transferId() != null) {
                    store.transferRecorded(candidate, razorpay.get("/transfers/" + candidate.transferId()));
                    continue;
                }
                boolean claimed = store.claimTransfer(candidate);
                var payment = store.get(candidate.rideId());
                if (payment.transferAccountId() == null) continue;
                JsonNode source = razorpay.get("/payments/" + payment.paymentId());
                if (!"captured".equals(source.path("status").asText()) || source.path("amount_refunded").asLong() != 0
                        || source.path("amount").asLong() != payment.amountPaise()
                        || !payment.orderId().equals(source.path("order_id").asText())) {
                    store.reconcileTransfer(payment.rideId());
                    continue;
                }
                JsonNode existing = razorpay.get("/payments/" + payment.paymentId() + "/transfers");
                JsonNode found = null;
                for (JsonNode transfer : existing.path("items")) {
                    if (String.valueOf(payment.rideId()).equals(transfer.path("notes").path("riderlink_ride_id").asText())) {
                        if (found != null) throw new IllegalStateException("Duplicate provider transfers require reconciliation");
                        found = transfer;
                    }
                }
                if (found != null) { store.transferRecorded(payment, found); continue; }
                if (!claimed) { store.reconcileTransfer(payment.rideId()); continue; }
                JsonNode response = razorpay.post("/payments/" + payment.paymentId() + "/transfers", Map.of("transfers", java.util.List.of(
                    Map.of("account", payment.transferAccountId(), "amount", payment.riderPaise(), "currency", "INR",
                            "notes", Map.of("riderlink_ride_id", String.valueOf(payment.rideId()))))));
                JsonNode transfers = response.path("items");
                if (!transfers.isArray() || transfers.size() != 1) throw new IllegalStateException("Transfer response requires reconciliation");
                store.transferRecorded(payment, transfers.get(0));
            } catch (Exception ex) {
                store.reconcileTransfer(candidate.rideId());
            }
        }
    }
}
