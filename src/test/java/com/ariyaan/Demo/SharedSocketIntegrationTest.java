package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Type;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.*;
import com.ariyaan.Demo.Service.*;
import com.ariyaan.Demo.config.JwtUtils;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="app.messaging.shared=true")
@EnabledIfEnvironmentVariable(named="PAYMENT_TEST_DB_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SharedSocketIntegrationTest {
    static final String schema="socket_test_"+UUID.randomUUID().toString().replace("-","");
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
    @AfterAll static void cleanupSchema() { if(admin!=null)admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired JwtUtils jwt;
    @Autowired SimpMessagingTemplate applicationMessages;
    @Autowired @Qualifier("brokerMessagingTemplate") SimpMessagingTemplate localMessages;
    @Autowired SharedMessageStore store;
    @Autowired SimpUserRegistry registry;
    @MockitoBean SharedMessageWorker scheduledWorker;
    @Test void committedDatabaseFanoutReachesAnAuthenticatedRealStompClientAsJson() throws Exception {
        db.update("INSERT INTO users(id,user_name,email,role,enabled) VALUES(3,'payer','payer@example.com','ADMIN',true)");
        db.update("INSERT INTO ride_details(ride_id,pickup_latitude,pickup_longitude,drop_latitude,drop_longitude,fare,chassis_no,vehicle_name,admin_id,ride_otp) VALUES(42,19,72,20,73,361,'chassis','bike',3,1234)");
        var client=new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompSession session=null;
        try {
            var headers=new StompHeaders();headers.set("Authorization","Bearer "+jwt.generateToken("payer"));
            session=client.connectAsync("http://localhost:"+port+"/ws",new org.springframework.web.socket.WebSocketHttpHeaders(),headers,new StompSessionHandlerAdapter(){}).get(10,TimeUnit.SECONDS);
            var received=new CompletableFuture<Map>();
            session.subscribe("/topic/user/3",new StompFrameHandler(){
                @Override public Type getPayloadType(StompHeaders h){return Map.class;}
                @Override public void handleFrame(StompHeaders h,Object payload){received.complete((Map)payload);}
            });
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline && (registry.getUser("payer")==null || registry.getUser("payer").getSessions().stream()
                    .noneMatch(s->s.getSubscriptions().stream().anyMatch(subscription->"/topic/user/3".equals(subscription.getDestination()))))) Thread.sleep(20);
            applicationMessages.convertAndSend("/topic/user/3",(Object)Map.of("type","RIDE_ACCEPTED","rideId",42));
            new SharedMessageWorker(store,localMessages).deliver();
            var payload=received.get(5,TimeUnit.SECONDS);
            assertEquals("RIDE_ACCEPTED",payload.get("type"));assertEquals(42,payload.get("rideId"));assertNotNull(payload.get("eventId"));
        } finally { if(session!=null && session.isConnected())session.disconnect();client.stop(); }
    }
}
