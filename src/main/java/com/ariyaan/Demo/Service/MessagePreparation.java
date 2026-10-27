package com.ariyaan.Demo.Service;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagePreparation {
    private final EventNotifications notifications;
    private final SharedMessageStore shared;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    public MessagePreparation(EventNotifications notifications,SharedMessageStore shared) { this.notifications=notifications;this.shared=shared; }
    @Transactional
    public Object prepare(String destination,Object raw,boolean fanout) {
        Object payload=raw;
        try {
            if(raw instanceof byte[] bytes) payload=json.readValue(bytes,Object.class);
            else if(!(raw instanceof String) && !(raw instanceof Map)) payload=json.convertValue(raw,Object.class);
        } catch(Exception ex) { throw new IllegalArgumentException("Application message must be JSON"); }
        UUID eventId=notifications.enqueue(destination,payload);
        if(eventId!=null && payload instanceof Map<?,?> map) {
            var copy=new LinkedHashMap<String,Object>();map.forEach((key,value)->copy.put(String.valueOf(key),value));
            copy.put("eventId",eventId.toString());payload=copy;
        }
        if(fanout) shared.enqueue(destination,payload);
        return payload;
    }
}
