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
    @MockitoBean DistanceService distance;
    @MockitoBean(name="committedMessages") SimpMessagingTemplate messages;
    @BeforeEach void fixtures() {
        db.execute("TRUNCATE users,rider_details,rider_verification_details,rider_location,ride_details,ride_request,invoices,ride_payments,checkout_sessions,confirmation_tokens,request_limits RESTART IDENTITY CASCADE");
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
}
