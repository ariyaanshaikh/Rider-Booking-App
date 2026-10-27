package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.ariyaan.Demo.Dto.RideDetailsDTO;
import com.ariyaan.Demo.Service.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named="PAYMENT_TEST_DB_URL",matches=".+")
class ProductionRidePostgresTest {
    static final String schema="ride_test_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate admin;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("PAYMENT_TEST_DB_URL");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"postgres","root"));
        admin.execute("CREATE SCHEMA "+schema);
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.datasource.username",()->"postgres");registry.add("spring.datasource.password",()->"root");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto",()->"none");
        registry.add("spring.flyway.enabled",()->"true");registry.add("spring.flyway.locations",()->"classpath:db/production");
        registry.add("spring.flyway.table",()->"production_schema_history");
    }
    @AfterAll static void cleanupSchema() { if(admin!=null) admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    @Autowired JdbcTemplate db;
    @Autowired RideDetailsService bookings;
    @Autowired RideService rides;
    @Autowired RidePaymentStore payments;
    @Autowired DispatchExpiry expiry;
    @Autowired PasswordResetService resets;
    @Autowired com.ariyaan.Demo.Repository.UserRepository users;
    @Autowired com.ariyaan.Demo.config.JwtUtils jwt;
    @Autowired EventNotifications notifications;
    @Autowired com.ariyaan.Demo.Controller.NotificationController devices;
    @MockitoBean DistanceService distance;
    @MockitoBean(name="committedMessages") SimpMessagingTemplate messages;
    @BeforeEach void fixtures() {
        db.execute("TRUNCATE users,rider_details,rider_verification_details,rider_location,ride_details,ride_request,invoices,ride_payments,checkout_sessions,confirmation_tokens,request_limits,socket_events,socket_deliveries,socket_nodes RESTART IDENTITY CASCADE");
        db.update("INSERT INTO users(id,user_name,email,role,enabled) VALUES(3,'payer','payer@example.com','ADMIN',true),(5,'rider','rider@example.com','RIDER',true),(6,'second','second@example.com','RIDER',true)");
        db.update("INSERT INTO rider_details(id,user_id,status) VALUES(9,5,'AVAILABLE'),(10,6,'AVAILABLE')");
        db.update("INSERT INTO rider_verification_details VALUES(9,'a','p','l','b','i','n',true),(10,'a2','p2','l2','b2','i2','n2',true)");
        db.update("INSERT INTO rider_location VALUES(9,19,72,now()),(10,19.001,72,now())");
        when(distance.getDistanceMeters(anyDouble(),anyDouble(),anyDouble(),anyDouble())).thenReturn(19000L);
    }
    RideDetailsDTO request() {
        var dto=new RideDetailsDTO();dto.setPickUpLatitude(19);dto.setPickUpLongitude(72);
        dto.setDropLatitude(20);dto.setDropLongitude(73);dto.setChassisNo("chassis");dto.setVehicleName("bike");
        return dto;
    }
    @Test void mobileRetryReturnsSameBookingAndChangedPayloadIsRejected() {
        var dto=request();int id=bookings.saveRideDetials(dto,"payer","booking_test_123");
        assertEquals(id,bookings.saveRideDetials(dto,"payer","booking_test_123"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM ride_details",Integer.class));
        dto.setVehicleName("other");
        assertThrows(IllegalStateException.class,()->bookings.saveRideDetials(dto,"payer","booking_test_123"));
    }
    @Test void concurrentBookingRetriesCreateOnlyOneRide() throws Exception {
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=workers.submit(()->bookings.saveRideDetials(request(),"payer","booking_concurrent"));
            var b=workers.submit(()->bookings.saveRideDetials(request(),"payer","booking_concurrent"));
            assertEquals(a.get(),b.get());
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM ride_details",Integer.class));
    }
    @Test void twoRidersRaceAndOnlyOneWins() throws Exception {
        int id=bookings.saveRideDetials(request(),"payer",null);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=workers.submit(()->rides.acceptRide(id,9,"rider"));
            var b=workers.submit(()->rides.acceptRide(id,10,"second"));
            assertNotEquals(a.get(),b.get());
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM rider_details WHERE status='ON_TRIP'",Integer.class));
    }
    @Test void cancellationRematchesPreviouslyNotifiedRiderWithoutDuplicateRows() {
        int id=bookings.saveRideDetials(request(),"payer",null);
        assertTrue(rides.acceptRide(id,9,"rider"));rides.riderCancel(id,9,"rider");
        assertEquals(2,db.queryForObject("SELECT count(*) FROM ride_request WHERE ride_id=?",Integer.class,id));
        assertTrue(rides.acceptRide(id,10,"second"));
    }
    @Test void bookingToInvoiceRequiresCapturedPaymentAndRepeatCompletionIsHarmless() throws Exception {
        int id=bookings.saveRideDetials(request(),"payer",null);assertTrue(rides.acceptRide(id,9,"rider"));
        int otp=db.queryForObject("SELECT ride_otp FROM ride_details WHERE ride_id=?",Integer.class,id);
        assertTrue(rides.startRide(id,9,otp,"rider"));
        assertThrows(RuntimeException.class,()->rides.endRide(id,9,"rider"));
        payments.beginOrder(id,"payer");
        var json=new ObjectMapper();
        payments.saveOrder(id,json.valueToTree(Map.of("id","order_test","receipt",payments.get(id).receipt(),"amount",36100,"currency","INR")));
        assertTrue(payments.captured(json.readTree("{\"id\":\"pay_test\",\"order_id\":\"order_test\",\"amount\":36100,\"currency\":\"INR\",\"captured\":true,\"status\":\"captured\"}")));
        assertTrue(rides.endRide(id,9,"rider"));assertTrue(rides.endRide(id,9,"rider"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM invoices",Integer.class));
        assertEquals("WAITING_ROUTE",payments.get(id).transferStatus());
    }
    @Test void staleDispatchExpiresAndCannotBeAccepted() {
        int id=bookings.saveRideDetials(request(),"payer",null);
        db.update("UPDATE ride_details SET created_at=now()-interval '20 minutes' WHERE ride_id=?",id);
        expiry.expire();
        assertFalse(rides.acceptRide(id,9,"rider"));
        assertEquals("ADMIN_CANCELLED",db.queryForObject("SELECT ride_status FROM ride_details WHERE ride_id=?",String.class,id));
    }
    org.springframework.security.core.Authentication auth(String username) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username,null,List.of());
    }
    void registerDevice(String username) {
        devices.register(auth(username),new com.ariyaan.Demo.Controller.NotificationController.Device("rider","installation_device_123","ExponentPushToken[abcdefgh12345678]","android"));
    }
    @Test void passwordRecoveryPreservesReadableOtpButRejectsReplayAndRevokesOldJwt() {
        String old=jwt.generateToken("payer");resets.request("payer@example.com");
        String otp=db.queryForObject("SELECT otp FROM password_resets WHERE user_id=3",String.class);
        assertTrue(otp.matches("[0-9]{6}"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM reset_mail_jobs",Integer.class));
        assertThrows(IllegalArgumentException.class,()->resets.verify("payer@example.com","invalid"));
        String token=(String)resets.verify("payer@example.com",otp).get("resetToken");
        assertThrows(IllegalArgumentException.class,()->resets.verify("payer@example.com",otp));
        resets.reset("payer@example.com",token,"new-password-123");
        var user=users.findByUserName("payer").orElseThrow();
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("new-password-123",user.getPassword()));
        assertFalse(jwt.validateToken(old,user));assertTrue(jwt.validateToken(jwt.generateToken("payer"),user));
        assertThrows(IllegalArgumentException.class,()->resets.reset("payer@example.com",token,"another-password"));
        assertEquals(otp,db.queryForObject("SELECT otp FROM password_resets WHERE user_id=3",String.class));
    }
    @Test void expiredResetAndUnknownAddressCannotChangePassword() {
        resets.request("missing@example.com");assertEquals(0,db.queryForObject("SELECT count(*) FROM reset_mail_jobs",Integer.class));
        resets.request("payer@example.com");String otp=db.queryForObject("SELECT otp FROM password_resets",String.class);
        db.update("UPDATE password_resets SET expires_at=now()-interval '1 second'");
        assertThrows(IllegalArgumentException.class,()->resets.verify("payer@example.com",otp));
        assertEquals(0,users.findByUserName("payer").orElseThrow().getCredentialVersion());
    }
    @Test void resetTokenCanOnlyBeUsedByOneConcurrentRequest() throws Exception {
        resets.request("payer@example.com");String otp=db.queryForObject("SELECT otp FROM password_resets",String.class);
        String token=(String)resets.verify("payer@example.com",otp).get("resetToken");
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> finish=()->{try{resets.reset("payer@example.com",token,"new-password-123");return true;}catch(IllegalArgumentException ex){return false;}};
            var a=workers.submit(finish);var b=workers.submit(finish);assertNotEquals(a.get(),b.get());
        }
        assertEquals(1,users.findByUserName("payer").orElseThrow().getCredentialVersion());
    }
    @Test void resetMailRetriesWithoutLosingOtp() {
        resets.request("payer@example.com");var email=mock(EmailService.class);
        doThrow(new IllegalStateException("offline")).doNothing().when(email).sendReset(anyString(),anyString());
        var worker=new ResetMailWorker(db,email);worker.deliver();
        assertEquals("PENDING",db.queryForObject("SELECT state FROM reset_mail_jobs",String.class));
        db.update("UPDATE reset_mail_jobs SET available_at=now()");worker.deliver();
        assertEquals("SENT",db.queryForObject("SELECT state FROM reset_mail_jobs",String.class));
    }
    @Test void pushDeliveryIsDurableDeduplicatedAndReceiptChecked() {
        int ride=bookings.saveRideDetials(request(),"payer",null);registerDevice("rider");
        var payload=Map.of("type","RIDE_STARTED","rideId",ride,"otp",1234);
        var event=notifications.enqueue("/topic/rider/9",payload);
        assertEquals(event,notifications.enqueue("/topic/rider/9",payload));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM push_jobs",Integer.class));
        var expo=mock(ExpoPushClient.class);when(expo.send(anyString(),anyMap(),anyInt())).thenReturn(new ExpoPushClient.Result("ok","ticket_test",""));
        var worker=new PushWorker(db,expo,true);worker.deliver();worker.deliver();
        org.mockito.ArgumentCaptor<Map<String,Object>> sent=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(expo,times(1)).send(anyString(),sent.capture(),anyInt());assertFalse(sent.getValue().containsKey("otp"));
        assertEquals("TICKET",db.queryForObject("SELECT state FROM push_jobs",String.class));
        when(expo.receipt("ticket_test")).thenReturn(new ExpoPushClient.Result("ok","",""));
        db.update("UPDATE push_jobs SET available_at=now()");worker.receipts();
        assertEquals("DELIVERED",db.queryForObject("SELECT state FROM push_jobs",String.class));
    }
    @Test void pushRetriesAndDisablesUnregisteredTokens() {
        int ride=bookings.saveRideDetials(request(),"payer",null);registerDevice("rider");
        notifications.enqueue("/topic/rider/9",Map.of("type","RIDE_ACCEPTED","rideId",ride));
        var expo=mock(ExpoPushClient.class);when(expo.send(anyString(),anyMap(),anyInt())).thenThrow(new IllegalStateException("timeout"))
                .thenReturn(new ExpoPushClient.Result("error","","DeviceNotRegistered"));
        var worker=new PushWorker(db,expo,true);worker.deliver();
        assertEquals("PENDING",db.queryForObject("SELECT state FROM push_jobs",String.class));
        db.update("UPDATE push_jobs SET available_at=now()");worker.deliver();
        assertEquals("FAILED",db.queryForObject("SELECT state FROM push_jobs",String.class));
        assertFalse(db.queryForObject("SELECT active FROM push_devices",Boolean.class));
    }
    @Test void accountSwitchAndLogoutNeverSendOldOwnersQueuedPushes() {
        int ride=bookings.saveRideDetials(request(),"payer",null);registerDevice("rider");
        notifications.enqueue("/topic/rider/9",Map.of("type","RIDE_ACCEPTED","rideId",ride));
        registerDevice("second");var expo=mock(ExpoPushClient.class);new PushWorker(db,expo,true).deliver();
        verifyNoInteractions(expo);assertEquals("CANCELLED",db.queryForObject("SELECT state FROM push_jobs",String.class));
        devices.unregister(auth("rider"),"rider","installation_device_123");
        assertTrue(db.queryForObject("SELECT active FROM push_devices",Boolean.class));
        devices.unregister(auth("second"),"rider","installation_device_123");
        assertFalse(db.queryForObject("SELECT active FROM push_devices",Boolean.class));
    }
    @Test void everyNodeReceivesCommittedMessagesOnceAndRecoversExpiredLeases() {
        var a=new SharedMessageStore(db,true);var b=new SharedMessageStore(db,true);a.start();b.start();
        a.enqueue("/topic/user/3",Map.of("type","RIDE_ACCEPTED","rideId",42));
        var first=a.claim();var second=b.claim();assertEquals(1,first.size());assertEquals(1,second.size());
        assertEquals(first.get(0).get("id"),second.get(0).get("id"));assertEquals(0,a.claim().size());
        db.update("UPDATE socket_deliveries SET lease_until=now()-interval '1 second'");
        var retried=a.claim();assertEquals(first.get(0).get("id"),retried.get(0).get("id"));
        a.delivered(retried.get(0).get("id"));b.delivered(second.get(0).get("id"));
        assertTrue(a.claim().isEmpty());assertTrue(b.claim().isEmpty());
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void rolledBackBusinessEventsLeaveNoPushOrSocketMessages() {
        int ride=bookings.saveRideDetials(request(),"payer",null);registerDevice("rider");
        var shared=new SharedMessageStore(db,true);
        assertThrows(IllegalStateException.class,()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
            notifications.enqueue("/topic/rider/9",Map.of("type","RIDE_STARTED","rideId",ride));
            shared.enqueue("/topic/rider/9",Map.of("type","RIDE_STARTED","rideId",ride));throw new IllegalStateException("rollback");
        }));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM notification_inbox",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM socket_events",Integer.class));
    }
    @Test void lateCommittingMessageIsNotSkippedAfterAnotherMessageWasDelivered() throws Exception {
        var store=new SharedMessageStore(db,true);store.start();
        var ready=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var workers=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var late=workers.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                store.enqueue("/topic/user/3",Map.of("type","LATE"));ready.countDown();
                try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("timeout");}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
                return true;
            }));
            try {
                assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));
                store.enqueue("/topic/user/3",Map.of("type","EARLY"));
                var first=store.claim();assertEquals(1,first.size());store.delivered(first.get(0).get("id"));
            } finally { release.countDown(); }
            assertTrue(late.get());var second=store.claim();assertEquals(1,second.size());
            assertEquals("LATE",((Map<?,?>)store.payload(second.get(0))).get("type"));
        }
    }
    @Test void twoPushWorkersDoNotClaimTheSameJob() throws Exception {
        int ride=bookings.saveRideDetials(request(),"payer",null);registerDevice("rider");
        notifications.enqueue("/topic/rider/9",Map.of("type","RIDE_STARTED","rideId",ride));
        var expo=mock(ExpoPushClient.class);when(expo.send(anyString(),anyMap(),anyInt())).thenReturn(new ExpoPushClient.Result("ok","ticket-test",""));
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->new PushWorker(db,expo,true).deliver());
            var second=workers.submit(()->new PushWorker(db,expo,true).deliver());first.get();second.get();
        }
        verify(expo,times(1)).send(anyString(),anyMap(),anyInt());
    }
}
