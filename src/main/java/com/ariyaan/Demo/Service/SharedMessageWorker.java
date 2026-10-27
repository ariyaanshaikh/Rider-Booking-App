package com.ariyaan.Demo.Service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.messaging.shared",havingValue="true")
public class SharedMessageWorker {
    private final SharedMessageStore store;
    private final SimpMessagingTemplate local;
    public SharedMessageWorker(SharedMessageStore store,@Qualifier("brokerMessagingTemplate") SimpMessagingTemplate local) {
        this.store=store;this.local=local;
    }
    @Scheduled(fixedDelay=500,initialDelay=2000)
    public void deliver() {
        for(var event:store.claim()) {
            try { local.convertAndSend(event.get("destination").toString(),store.payload(event));store.delivered(event.get("id")); }
            catch(RuntimeException ex) { /* The lease expires and this node retries. Other nodes have independent claims. */ }
        }
    }
    @Scheduled(fixedDelay=3600000,initialDelay=3600000)
    public void prune() { store.prune(); }
}
