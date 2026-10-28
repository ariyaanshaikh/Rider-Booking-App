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
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ariyaan.Demo.Dto.RideDetailsDTO;
import com.ariyaan.Demo.Service.*;
import com.ariyaan.Demo.Controller.NotificationController;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Real services, real message preparation and real PostgreSQL; no socket-template mock. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties="app.messaging.shared=true")
@EnabledIfEnvironmentVariable(named="PAYMENT_TEST_DB_URL",matches=".+")
class RidePushLifecyclePostgresTest {
    static final String schema="push_lifecycle_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate admin;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("PAYMENT_TEST_DB_URL");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"postgres","root"));admin.execute("CREATE SCHEMA "+schema);
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.datasource.username",()->"postgres");registry.add("spring.datasource.password",()->"root");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto",()->"none");
        registry.add("spring.flyway.enabled",()->"true");registry.add("spring.flyway.locations",()->"classpath:db/production");
        registry.add("spring.flyway.table",()->"production_schema_history");
    }
    @AfterAll static void cleanup(){if(admin!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    @Autowired JdbcTemplate db;
    @Autowired RideDetailsService bookings;
    @Autowired RideService rides;
    @Autowired RidePaymentStore payments;
    @Autowired EventNotifications events;
    @Autowired NotificationController devices;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean DistanceService distance;
    @BeforeEach void fixture(){
        db.execute("TRUNCATE users,rider_details,rider_verification_details,rider_location,ride_details,ride_request,invoices,request_limits,socket_nodes,socket_events RESTART IDENTITY CASCADE");
        db.update("INSERT INTO users(id,user_name,email,role,enabled) VALUES(3,'payer','payer@example.com','ADMIN',true),(4,'otherAdmin','other@example.com','ADMIN',true),(5,'rider','rider@example.com','RIDER',true),(6,'second','second@example.com','RIDER',true)");
        db.update("INSERT INTO rider_details(id,user_id,status) VALUES(9,5,'AVAILABLE'),(10,6,'AVAILABLE')");
        db.update("INSERT INTO rider_verification_details VALUES(9,'a','p','l','b','i','n',true),(10,'a2','p2','l2','b2','i2','n2',true)");
        db.update("INSERT INTO rider_location VALUES(9,19,72,now()),(10,19.001,72,now())");
        when(distance.getDistanceMeters(anyDouble(),anyDouble(),anyDouble(),anyDouble())).thenReturn(19000L);
        register("rider","rider","one");register("second","rider","two");register("payer","admin","three");register("otherAdmin","admin","four");
    }
    void register(String username,String app,String suffix){
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username,null,List.of());
        devices.register(auth,new NotificationController.Device(app,"installation_device_"+suffix,"ExponentPushToken[device12345678"+suffix+"]","ios"));
    }
    int book(){
        var dto=new RideDetailsDTO();dto.setPickUpLatitude(19);dto.setPickUpLongitude(72);dto.setDropLatitude(20);dto.setDropLongitude(73);dto.setChassisNo("bike-id");dto.setVehicleName("bike");
        return bookings.saveRideDetials(dto,"payer","push_booking_test");
    }
    int count(String sql,Object... args){return db.queryForObject(sql,Integer.class,args);}
    @Test void bookingCommitsPrivateOfferJobsAndProviderFailureCannotRollBackAssignment(){
        int id=book();assertEquals(id,book());
        assertEquals(2,count("SELECT count(*) FROM ride_request WHERE ride_id=? AND status='SENT'",id));
        assertEquals(2,count("SELECT count(*) FROM push_jobs j JOIN notification_inbox n ON n.id=j.notification_id WHERE n.type='RIDE_REQUEST' AND j.app='rider'"));
        assertEquals(0,count("SELECT count(*) FROM notification_inbox WHERE user_id IN (3,4)"));
        var event=events.enqueue("/topic/rider/9",Map.of("type","RIDE_REQUEST","rideId",id));
        assertEquals(event,events.enqueue("/topic/rider/9",Map.of("type","RIDE_REQUEST","rideId",id)));
        assertEquals(2,count("SELECT count(*) FROM push_jobs"));
        var expo=mock(ExpoPushClient.class);when(expo.send(anyString(),anyMap(),anyInt())).thenThrow(new IllegalStateException("provider down"));
        new PushWorker(db,expo,true).deliver();
        assertEquals("REQUESTED",db.queryForObject("SELECT ride_status FROM ride_details WHERE ride_id=?",String.class,id));
        assertEquals(2,count("SELECT count(*) FROM push_jobs WHERE state='PENDING' AND attempts=1"));
    }
    @Test void completionRequiresCaptureAndQueuesOneAdminPushAndCommittedSocketEvent() throws Exception {
        int id=book();assertTrue(rides.acceptRide(id,9,"rider"));
        int otp=db.queryForObject("SELECT ride_otp FROM ride_details WHERE ride_id=?",Integer.class,id);
        assertTrue(rides.startRide(id,9,otp,"rider"));assertThrows(RuntimeException.class,()->rides.endRide(id,9,"rider"));
        assertEquals(1,count("SELECT count(*) FROM notification_inbox WHERE user_id=3 AND type='PAYMENT_REQUIRED'"));
        assertEquals(0,count("SELECT count(*) FROM notification_inbox WHERE type='RIDE_COMPLETED'"));
        payments.beginOrder(id,"payer");var json=new ObjectMapper();
        payments.saveOrder(id,json.valueToTree(Map.of("id","order_push","receipt",payments.get(id).receipt(),"amount",36100,"currency","INR")));
        payments.captured(json.readTree("{\"id\":\"pay_push\",\"order_id\":\"order_push\",\"amount\":36100,\"currency\":\"INR\",\"captured\":true,\"status\":\"captured\"}"));
        assertTrue(rides.endRide(id,9,"rider"));assertTrue(rides.endRide(id,9,"rider"));
        assertEquals("COMPLETED",db.queryForObject("SELECT ride_status FROM ride_details WHERE ride_id=?",String.class,id));
        assertEquals(1,count("SELECT count(*) FROM invoices WHERE ride_id=?",id));
        assertEquals(1,count("SELECT count(*) FROM push_jobs j JOIN notification_inbox n ON n.id=j.notification_id WHERE n.user_id=3 AND n.type='RIDE_COMPLETED' AND j.app='admin'"));
        assertEquals(0,count("SELECT count(*) FROM notification_inbox WHERE user_id=4"));
        assertEquals(1,count("SELECT count(*) FROM socket_events WHERE destination='/topic/user/3' AND payload->>'type'='RIDE_COMPLETED' AND payload->>'eventId' IS NOT NULL"));
    }
    @Test void expiredAcceptedAndSupersededOffersAreNotDelivered(){
        int id=book();
        db.update("UPDATE ride_request SET created_at=created_at+interval '1 second' WHERE rider_id=9 AND ride_id=?",id);
        db.update("UPDATE ride_details SET created_at=now()-interval '20 minutes' WHERE ride_id=?",id);
        var expo=mock(ExpoPushClient.class);new PushWorker(db,expo,true).deliver();verifyNoInteractions(expo);
        assertEquals(2,count("SELECT count(*) FROM push_jobs WHERE state='CANCELLED'"));
    }
    @Test void aNewRematchAttemptGetsItsOwnAlertButThePreviousOfferCannotBeSent(){
        int id=book();var before=events.enqueue("/topic/rider/10",Map.of("type","RIDE_REQUEST","rideId",id));
        assertTrue(rides.acceptRide(id,9,"rider"));rides.riderCancel(id,9,"rider");
        var after=events.enqueue("/topic/rider/10",Map.of("type","RIDE_REQUEST","rideId",id));
        assertNotEquals(before,after);
        var expo=mock(ExpoPushClient.class);when(expo.send(anyString(),anyMap(),anyInt())).thenReturn(new ExpoPushClient.Result("ok","ticket",""));
        new PushWorker(db,expo,true).deliver();
        assertEquals("CANCELLED",db.queryForObject("SELECT state FROM push_jobs WHERE notification_id=?",String.class,before));
    }
    @Test void outerRollbackLeavesNoAssignmentInboxOrPushJobs(){
        assertThrows(IllegalStateException.class,()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{book();throw new IllegalStateException("rollback");}));
        assertEquals(0,count("SELECT count(*) FROM ride_details"));assertEquals(0,count("SELECT count(*) FROM notification_inbox"));assertEquals(0,count("SELECT count(*) FROM push_jobs"));
    }
    @Test void requestsToBusyRidersOrPastPushExpiryAreNeverSent(){
        book();db.update("UPDATE rider_details SET status='ON_TRIP'");
        var expo=mock(ExpoPushClient.class);new PushWorker(db,expo,true).deliver();verifyNoInteractions(expo);
        assertEquals(2,count("SELECT count(*) FROM push_jobs WHERE state='CANCELLED'"));
        db.update("UPDATE push_jobs SET state='PENDING',expires_at=now()-interval '1 second'");
        new PushWorker(db,expo,true).deliver();verifyNoInteractions(expo);
        assertEquals(2,count("SELECT count(*) FROM push_jobs WHERE state='CANCELLED'"));
    }
    @Test void customerCancellationNotifiesAllOutstandingOfferOwnersWithoutChangingPaymentRules(){
        int id=book();rides.userCancel(id,"payer");
        assertEquals(2,count("SELECT count(*) FROM notification_inbox WHERE type='RIDE_CANCELLED_BY_USER' AND user_id IN (5,6)"));
        var expo=mock(ExpoPushClient.class);when(expo.send(anyString(),anyMap(),anyInt())).thenReturn(new ExpoPushClient.Result("ok","ticket",""));
        new PushWorker(db,expo,true).deliver();
        assertEquals(2,count("SELECT count(*) FROM push_jobs j JOIN notification_inbox n ON n.id=j.notification_id WHERE n.type='RIDE_REQUEST' AND j.state='CANCELLED'"));
        assertEquals("ADMIN_CANCELLED",db.queryForObject("SELECT ride_status FROM ride_details WHERE ride_id=?",String.class,id));
    }
}
