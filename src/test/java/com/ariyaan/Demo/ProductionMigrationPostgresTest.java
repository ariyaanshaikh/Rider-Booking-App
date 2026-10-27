package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import com.ariyaan.Demo.Service.*;

@EnabledIfEnvironmentVariable(named="PAYMENT_TEST_DB_URL",matches=".+")
class ProductionMigrationPostgresTest {
    JdbcTemplate admin,db; String schema; DriverManagerDataSource source;
    @BeforeEach void setup() {
        String url=System.getenv("PAYMENT_TEST_DB_URL");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"postgres","root"));
        schema="production_test_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+schema);
        source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,"postgres","root");
        db=new JdbcTemplate(source);
    }
    @AfterEach void cleanup() { if(admin!=null && schema!=null) admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    void migrate() { Flyway.configure().dataSource(source).locations("classpath:db/production").table("production_schema_history")
            .baselineOnMigrate(true).baselineVersion("0").load().migrate(); }
    @Test void emptyDatabaseMigratesAndSecondRunIsSafe() {
        migrate();migrate();
        assertNotNull(db.queryForObject("SELECT count(*) FROM ride_payments",Integer.class));
        assertNotNull(db.queryForObject("SELECT count(*) FROM request_limits",Integer.class));
        assertNotNull(db.queryForObject("SELECT count(*) FROM checkout_sessions",Integer.class));
    }
    @Test void manuallyInitializedDatabasePreservesExistingAccountsAndOldHistory() throws Exception {
        String sql=Files.readString(Path.of("docs/DATABASE_SETUP_AND_REFERENCE.sql"));
        db.execute(sql.substring(sql.indexOf("CREATE TABLE"),sql.indexOf("-- Matching reference query")));
        db.execute("CREATE TABLE flyway_schema_history(version text)");
        db.execute("INSERT INTO users(id,user_name,email,role) VALUES(3,'existing','existing@example.com','ADMIN')");
        migrate();
        assertEquals("existing",db.queryForObject("SELECT user_name FROM users WHERE id=3",String.class));
        assertNotNull(db.queryForObject("SELECT count(*) FROM flyway_schema_history",Integer.class));
    }
    @Test void attemptsAreSharedAndWindowExpires() {
        migrate();var limits=new RequestLimits(db);
        limits.check("email-otp","alice@example.com",2);limits.check("email-otp","alice@example.com",2);
        assertThrows(RequestLimits.TooManyRequests.class,()->new RequestLimits(db).check("email-otp","alice@example.com",2));
        db.update("UPDATE request_limits SET window_start=now()-interval '1 hour'");
        assertDoesNotThrow(()->limits.check("email-otp","alice@example.com",2));
    }
    @Test void checkoutSessionsAreRideScopedExpiringAndDoNotContainAccountJwt() {
        migrate();
        db.update("INSERT INTO ride_details(ride_id,pickup_latitude,pickup_longitude,drop_latitude,drop_longitude,fare,chassis_no,vehicle_name,admin_id,ride_otp) VALUES(42,19,72,20,73,19,'chassis','bike',3,1234)");
        var store=mock(RidePaymentStore.class);var provider=mock(RazorpayClient.class);
        var sessions=new CheckoutSessions(db,store,provider,"https://example.com");
        String url=(String)sessions.create(42,"payer").get("checkoutUrl");
        String token=url.substring(url.indexOf("session=")+8);
        assertEquals(new CheckoutSessions.Session(42,"payer"),sessions.resolve(token));
        assertNotEquals(token,db.queryForObject("SELECT token_hash FROM checkout_sessions",String.class));
        assertThrows(RuntimeException.class,()->sessions.resolve("invalid"));
        db.update("UPDATE checkout_sessions SET expires_at=now()-interval '1 second'");
        assertThrows(RuntimeException.class,()->sessions.resolve(token));
    }
}
