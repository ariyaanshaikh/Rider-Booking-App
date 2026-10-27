package com.ariyaan.Demo.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL fanout to every application node. No process-local cursor can skip a late-committing transaction. */
@Service
public class SharedMessageStore {
    private final JdbcTemplate db;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final UUID node=UUID.randomUUID();
    private volatile Timestamp born;
    private final boolean enabled;
    public SharedMessageStore(JdbcTemplate db,@Value("${app.messaging.shared:false}") boolean enabled) { this.db=db;this.enabled=enabled; }
    @EventListener(ApplicationReadyEvent.class)
    public void start() { if(enabled) register(); }
    private synchronized void register() {
        if(born==null) born=db.queryForObject("INSERT INTO socket_nodes(id) VALUES(?) RETURNING heartbeat_at-interval '30 seconds'",Timestamp.class,node);
    }
    public void enqueue(String destination,Object payload) {
        try { db.update("INSERT INTO socket_events(id,destination,payload) VALUES(?,?,?::jsonb)",UUID.randomUUID(),destination,json.writeValueAsString(payload)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("Message cannot be serialized"); }
    }
    @Transactional
    public List<Map<String,Object>> claim() {
        register();
        db.update("UPDATE socket_nodes SET heartbeat_at=now() WHERE id=?",node);
        db.update("""
            INSERT INTO socket_deliveries(node_id,event_id)
            SELECT ?,e.id FROM socket_events e WHERE e.created_at>=? AND e.created_at>now()-interval '1 hour'
            AND NOT EXISTS(SELECT 1 FROM socket_deliveries d WHERE d.node_id=? AND d.event_id=e.id)
            ORDER BY e.created_at,e.id LIMIT 100 ON CONFLICT DO NOTHING
            """,node,born,node);
        return db.queryForList("""
            WITH due AS (SELECT event_id FROM socket_deliveries WHERE node_id=? AND NOT delivered
                AND (lease_until IS NULL OR lease_until<now()) ORDER BY event_id LIMIT 100 FOR UPDATE SKIP LOCKED)
            UPDATE socket_deliveries d SET lease_until=now()+interval '30 seconds'
            FROM due,socket_events e WHERE d.node_id=? AND d.event_id=due.event_id AND e.id=d.event_id
            RETURNING e.id,e.destination,e.payload::text
            """,node,node);
    }
    public Object payload(Map<String,Object> message) {
        try { return json.readValue(message.get("payload").toString(),Object.class); }
        catch(Exception ex) { throw new IllegalStateException("Stored message is invalid"); }
    }
    public void delivered(Object id) { db.update("UPDATE socket_deliveries SET delivered=true,lease_until=NULL WHERE node_id=? AND event_id=?",node,id); }
    public void prune() {
        db.update("DELETE FROM socket_events WHERE created_at<now()-interval '1 hour'");
        db.update("DELETE FROM socket_deliveries WHERE node_id IN (SELECT id FROM socket_nodes WHERE heartbeat_at<now()-interval '2 days')");
        db.update("DELETE FROM socket_nodes WHERE heartbeat_at<now()-interval '2 days'");
    }
}
