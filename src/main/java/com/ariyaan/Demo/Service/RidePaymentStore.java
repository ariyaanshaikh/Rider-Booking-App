package com.ariyaan.Demo.Service;

import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.fasterxml.jackson.databind.JsonNode;

/** Durable intent is committed before provider calls, so timeouts never cause blind retries. */
@Service
public class RidePaymentStore {
    private final JdbcTemplate db;
    public RidePaymentStore(JdbcTemplate db) { this.db = db; }
    public record Payment(int rideId, long amountPaise, long businessPaise, long riderPaise,
            String receipt, String orderId, String paymentId, String status,
            String transferStatus, String transferId, String transferAccountId) {}
    public record OrderIntent(Payment payment, boolean created) {}
    private Payment map(ResultSet r, int n) throws SQLException {
        return new Payment(r.getInt("ride_id"), r.getLong("amount_paise"), r.getLong("business_paise"),
            r.getLong("rider_paise"), r.getString("receipt"), r.getString("order_id"), r.getString("payment_id"),
            r.getString("status"), r.getString("transfer_status"), r.getString("transfer_id"), r.getString("transfer_account_id"));
    }
    public Payment get(int rideId) {
        return db.query("SELECT * FROM ride_payments WHERE ride_id=?", this::map, rideId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No payment order exists for this ride"));
    }
    public int rideIdForOrder(String orderId) {
        return db.queryForObject("SELECT ride_id FROM ride_payments WHERE order_id=?", Integer.class, orderId);
    }
    public void authorize(int rideId, String username, boolean adminOnly) {
        Integer count = db.queryForObject("""
            SELECT COUNT(*) FROM ride_details r JOIN users u ON u.user_name=? AND u.enabled=TRUE
            WHERE r.ride_id=? AND ((u.role='ADMIN' AND u.id=r.admin_id)
                OR (?=FALSE AND u.role='RIDER' AND EXISTS
                    (SELECT 1 FROM rider_details rd WHERE rd.id=r.rider_id AND rd.user_id=u.id)))
            """, Integer.class, username, rideId, adminOnly);
        if (count == null || count != 1) throw new AccessDeniedException("This ride does not belong to your account");
    }
    @Transactional
    public OrderIntent beginOrder(int rideId, String username) {
        authorize(rideId, username, true);
        Map<String,Object> ride = db.queryForMap("SELECT * FROM ride_details WHERE ride_id=? FOR UPDATE", rideId);
        if (!"AWAITING_PAYMENT".equals(ride.get("ride_status")))
            throw new IllegalStateException("Verify the ride OTP before creating a payment order");
        if (ride.get("distance_meters") == null)
            throw new IllegalStateException("Legacy ride has no server-priced distance; create a new booking");
        FarePolicy.Quote quote = FarePolicy.quote(((Number)ride.get("distance_meters")).longValue());
        long fare = new BigDecimal(ride.get("fare").toString()).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (fare != quote.amountPaise()) throw new IllegalStateException("Stored fare does not match the route quote");
        int inserted = db.update("""
            INSERT INTO ride_payments(ride_id,amount_paise,business_paise,rider_paise,receipt)
            VALUES (?,?,?,?,?) ON CONFLICT (ride_id) DO NOTHING
            """, rideId, quote.amountPaise(), quote.businessPaise(), quote.riderPaise(),
                "rl_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        return new OrderIntent(get(rideId), inserted == 1);
    }
    public void saveOrder(int rideId, JsonNode order) {
        Payment payment = get(rideId);
        if (!order.path("id").asText().matches("order_[A-Za-z0-9]+")
                || order.path("amount").asLong() != payment.amountPaise()
                || !"INR".equals(order.path("currency").asText())
                || !payment.receipt().equals(order.path("receipt").asText()))
            throw new IllegalStateException("Razorpay order does not match the stored quote");
        db.update("""
            UPDATE ride_payments SET order_id=?,status='ORDER_CREATED',updated_at=NOW()
            WHERE ride_id=? AND order_id IS NULL AND status='CREATING'
            """, order.path("id").asText(), rideId);
    }
    @Transactional
    public boolean captured(JsonNode entity) {
        String orderId = entity.path("order_id").asText();
        List<Payment> rows = db.query("SELECT * FROM ride_payments WHERE order_id=?", this::map, orderId);
        if (rows.isEmpty()) return false; // unrelated merchant payment; do not touch rides
        Payment original = rows.get(0);
        Map<String,Object> ride = db.queryForMap("SELECT * FROM ride_details WHERE ride_id=? FOR UPDATE", original.rideId());
        Payment payment = db.queryForObject("SELECT * FROM ride_payments WHERE ride_id=? FOR UPDATE", this::map, original.rideId());
        if (!"captured".equals(entity.path("status").asText()) || !entity.path("captured").asBoolean()
                || entity.path("amount_refunded").asLong() > 0
                || entity.path("amount").asLong() != payment.amountPaise()
                || !"INR".equals(entity.path("currency").asText())
                || !entity.path("id").asText().matches("pay_[A-Za-z0-9]+"))
            throw new IllegalArgumentException("Payment amount, currency or capture state does not match");
        if (payment.paymentId() != null && !payment.paymentId().equals(entity.path("id").asText()))
            throw new IllegalStateException("A different payment is already recorded for this order");
        if (List.of("CAPTURED", "REFUND_REQUIRED", "REFUNDED").contains(payment.status())) return false;
        boolean start = "AWAITING_PAYMENT".equals(ride.get("ride_status"));
        db.update("""
            UPDATE ride_payments SET status=?,payment_id=?,updated_at=NOW() WHERE ride_id=?
            """, start ? "CAPTURED" : "REFUND_REQUIRED", entity.path("id").asText(), payment.rideId());
        db.update("UPDATE ride_details SET payment_completed=? WHERE ride_id=?",
                start ? "CAPTURED" : "REFUND_REQUIRED", payment.rideId());
        if (start) db.update("UPDATE ride_details SET ride_status='STARTED' WHERE ride_id=?", payment.rideId());
        return start;
    }
    @Transactional
    public void recordWebhook(String eventId, String type, Runnable action) {
        int inserted = db.update("""
            INSERT INTO razorpay_webhook_events(event_id,event_type) VALUES (?,?) ON CONFLICT DO NOTHING
            """, eventId, type);
        if (inserted == 1) action.run();
    }
    public void refunded(JsonNode entity) {
        // A partial refund also blocks an automatic rider transfer pending finance review.
        db.update("""
            UPDATE ride_payments SET status=CASE WHEN amount_paise=? THEN 'REFUNDED' ELSE 'REFUND_REQUIRED' END,
            updated_at=NOW() WHERE payment_id=?
            """, entity.path("amount").asLong(), entity.path("payment_id").asText());
        db.update("UPDATE ride_details r SET payment_completed=p.status FROM ride_payments p WHERE r.ride_id=p.ride_id AND p.payment_id=?", entity.path("payment_id").asText());
    }
    public void transferEvent(JsonNode entity) {
        String status = entity.path("status").asText();
        if (!List.of("processed", "failed").contains(status)) return;
        db.update("""
            UPDATE ride_payments SET transfer_status=?,updated_at=NOW()
            WHERE transfer_id=? AND transfer_status NOT IN ('SETTLED','PROCESSED')
            """, status.toUpperCase(java.util.Locale.ROOT), entity.path("id").asText());
    }
    public void enqueueCompleted(int rideId) {
        db.update("""
            UPDATE ride_payments SET transfer_status='WAITING_ROUTE',updated_at=NOW()
            WHERE ride_id=? AND status='CAPTURED' AND transfer_status='NOT_ELIGIBLE'
            """, rideId);
    }
    public boolean isCaptured(int rideId) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM ride_payments WHERE ride_id=? AND status='CAPTURED')", Boolean.class, rideId));
    }
    public List<Payment> transferable() {
        return db.query("""
            SELECT p.* FROM ride_payments p JOIN ride_details r ON r.ride_id=p.ride_id
            WHERE r.ride_status='COMPLETED' AND p.status='CAPTURED'
            AND p.transfer_status IN ('WAITING_ROUTE','READY','SUBMITTING','RECONCILE','PROCESSED')
            ORDER BY p.updated_at LIMIT 25
            """, this::map);
    }
    public boolean claimTransfer(Payment payment) {
        return db.update("""
            UPDATE ride_payments p SET transfer_status='SUBMITTING',transfer_account_id=a.account_id,updated_at=NOW()
            FROM rider_route_accounts a, ride_details r
            WHERE p.ride_id=? AND r.ride_id=p.ride_id AND r.rider_id=a.rider_id
            AND a.approved=TRUE AND r.ride_status='COMPLETED' AND p.status='CAPTURED'
            AND p.transfer_status IN ('WAITING_ROUTE','READY')
            """, payment.rideId()) == 1;
    }
    public void transferRecorded(Payment payment, JsonNode transfer) {
        if (!payment.transferAccountId().equals(transfer.path("recipient").asText())
                || transfer.path("amount").asLong() != payment.riderPaise()
                || !"INR".equals(transfer.path("currency").asText())
                || !transfer.path("id").asText().matches("trf_[A-Za-z0-9]+"))
            throw new IllegalStateException("Transfer does not match the stored rider allocation");
        String state = "processed".equals(transfer.path("status").asText()) ?
                ("settled".equals(transfer.path("settlement_status").asText()) ? "SETTLED" : "PROCESSED") :
                "failed".equals(transfer.path("status").asText()) ? "FAILED" : "RECONCILE";
        db.update("UPDATE ride_payments SET transfer_id=?,transfer_status=?,last_error=NULL,updated_at=NOW() WHERE ride_id=?",
                transfer.path("id").asText(), state, payment.rideId());
    }
    public void reconcileTransfer(int rideId) {
        db.update("UPDATE ride_payments SET transfer_status='RECONCILE',last_error='Provider result uncertain; reconciliation required',updated_at=NOW() WHERE ride_id=? AND transfer_status IN ('SUBMITTING','RECONCILE')", rideId);
    }
}
