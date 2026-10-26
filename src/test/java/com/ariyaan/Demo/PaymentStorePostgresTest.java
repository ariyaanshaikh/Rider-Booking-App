package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.ariyaan.Demo.Service.RidePaymentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/** Uses an isolated schema, never the application's tables or external payments. */
@EnabledIfEnvironmentVariable(named="PAYMENT_TEST_DB_URL", matches=".+")
class PaymentStorePostgresTest {
    @Configuration @EnableTransactionManagement static class TxConfig {}
    AnnotationConfigApplicationContext context;
    JdbcTemplate db, admin;
    RidePaymentStore store;
    String schema;
    final ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        String url = System.getenv("PAYMENT_TEST_DB_URL");
        var base = new DriverManagerDataSource(url, "postgres", "root");
        admin = new JdbcTemplate(base);
        schema = "payment_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        var source = new DriverManagerDataSource(url + "?currentSchema=" + schema, "postgres", "root");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,user_name TEXT,role TEXT,enabled BOOLEAN)");
        db.execute("CREATE TABLE rider_details(id BIGINT PRIMARY KEY,user_id BIGINT)");
        db.execute("CREATE TABLE ride_details(ride_id INTEGER PRIMARY KEY,admin_id BIGINT,rider_id BIGINT,fare NUMERIC(12,2),ride_status TEXT)");
        db.execute("CREATE TABLE invoices(status TEXT)");
        db.execute(Files.readString(Path.of("src/main/resources/db/migration/V10__razorpay_payments.sql")));
        db.execute("INSERT INTO users VALUES (3,'payer','ADMIN',TRUE),(4,'other','ADMIN',TRUE),(5,'rider','RIDER',TRUE)");
        db.execute("INSERT INTO rider_details VALUES (9,5)");
        db.execute("INSERT INTO ride_details(ride_id,admin_id,rider_id,fare,ride_status,distance_meters) VALUES (42,3,9,361,'AWAITING_PAYMENT',19000)");
        context = new AnnotationConfigApplicationContext();
        context.register(TxConfig.class);
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(JdbcTemplate.class, () -> db);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(RidePaymentStore.class);
        context.refresh();
        store = context.getBean(RidePaymentStore.class);
    }
    @AfterEach void cleanup() {
        if (context != null) context.close();
        if (admin != null && schema != null) admin.execute("DROP SCHEMA " + schema + " CASCADE");
    }
    void order() throws Exception {
        store.beginOrder(42,"payer");
        store.saveOrder(42,json.valueToTree(java.util.Map.of("id","order_test","receipt",store.get(42).receipt(),"amount",36100,"currency","INR")));
    }
    JsonNode capture() throws Exception {
        return json.readTree("{\"id\":\"pay_test\",\"order_id\":\"order_test\",\"amount\":36100,\"currency\":\"INR\",\"captured\":true,\"status\":\"captured\"}");
    }
    @Test void onlyBookingAdminCanCreateAnOrderAndQuoteIsImmutable() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> store.beginOrder(42,"other"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> store.beginOrder(42,"rider"));
        assertTrue(store.beginOrder(42,"payer").created());
        assertFalse(store.beginOrder(42,"payer").created());
        assertEquals(32490,store.get(42).riderPaise());
        db.update("UPDATE ride_details SET fare=1 WHERE ride_id=42");
        assertThrows(IllegalStateException.class, () -> store.beginOrder(42,"payer"));
    }
    @Test void capturedStartsRideOnceAndDoesNotReleaseBeforeCompletion() throws Exception {
        order();
        assertTrue(store.captured(capture()));
        assertFalse(store.captured(capture()));
        assertEquals("STARTED", db.queryForObject("SELECT ride_status FROM ride_details WHERE ride_id=42",String.class));
        assertEquals("CAPTURED",store.get(42).status());
        assertEquals("NOT_ELIGIBLE",store.get(42).transferStatus());
        assertTrue(store.transferable().isEmpty());
    }
    @Test void wrongAmountAndCurrencyNeverStartRide() throws Exception {
        order();
        var wrong=(com.fasterxml.jackson.databind.node.ObjectNode)capture(); wrong.put("amount",1);
        assertThrows(IllegalArgumentException.class, () -> store.captured(wrong));
        wrong.put("amount",36100); wrong.put("currency","USD");
        assertThrows(IllegalArgumentException.class, () -> store.captured(wrong));
        assertEquals("ORDER_CREATED",store.get(42).status());
        assertEquals("AWAITING_PAYMENT",db.queryForObject("SELECT ride_status FROM ride_details WHERE ride_id=42",String.class));
    }
    @Test void duplicateWebhookIsHarmlessAndFailedProcessingRollsBackReceipt() {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        store.recordWebhook("evt1","payment.captured",count::incrementAndGet);
        store.recordWebhook("evt1","payment.captured",count::incrementAndGet);
        assertEquals(1,count.get());
        assertThrows(IllegalStateException.class, () -> store.recordWebhook("evt2","payment.captured",() -> {throw new IllegalStateException("fail");}));
        store.recordWebhook("evt2","payment.captured",count::incrementAndGet);
        assertEquals(2,count.get());
    }
    @Test void latePaymentCannotRestartCancelledRide() throws Exception {
        order(); db.update("UPDATE ride_details SET ride_status='ADMIN_CANCELLED' WHERE ride_id=42");
        assertFalse(store.captured(capture()));
        assertEquals("REFUND_REQUIRED",store.get(42).status());
        assertEquals("ADMIN_CANCELLED",db.queryForObject("SELECT ride_status FROM ride_details WHERE ride_id=42",String.class));
    }
    @Test void transferClaimIsSingleUseAndRequiresCompletionAndApprovedAccount() throws Exception {
        order(); store.captured(capture()); store.enqueueCompleted(42);
        assertFalse(store.claimTransfer(store.get(42)));
        db.update("UPDATE ride_details SET ride_status='COMPLETED' WHERE ride_id=42");
        assertFalse(store.claimTransfer(store.get(42)));
        db.update("INSERT INTO rider_route_accounts(rider_id,account_id,approved) VALUES (9,'acc_test',TRUE)");
        assertTrue(store.claimTransfer(store.get(42)));
        assertFalse(store.claimTransfer(store.get(42)));
        assertEquals("acc_test",store.get(42).transferAccountId());
    }
    @Test void migrationCanBeReappliedWithoutLosingData() throws Exception {
        order();
        db.execute(Files.readString(Path.of("src/main/resources/db/migration/V10__razorpay_payments.sql")));
        assertEquals("order_test",store.get(42).orderId());
    }
    @Test void concurrentOrderRequestsCreateOneDurableIntent() throws Exception {
        try (var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=workers.submit(() -> store.beginOrder(42,"payer"));
            var second=workers.submit(() -> store.beginOrder(42,"payer"));
            assertNotEquals(first.get().created(),second.get().created());
            assertEquals(first.get().payment().receipt(),second.get().payment().receipt());
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ride_payments",Integer.class));
        }
    }
    @Test void concurrentCaptureChangesRideOnce() throws Exception {
        order(); var entity=capture();
        try (var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=workers.submit(() -> store.captured(entity));
            var second=workers.submit(() -> store.captured(entity));
            assertNotEquals(first.get(),second.get());
            assertEquals("CAPTURED",store.get(42).status());
        }
    }
    @Test void partialRefundBlocksRiderTransfer() throws Exception {
        order(); store.captured(capture());
        store.refunded(json.readTree("{\"payment_id\":\"pay_test\",\"amount\":100}"));
        assertEquals("REFUND_REQUIRED",store.get(42).status());
        assertTrue(store.transferable().isEmpty());
    }
}
