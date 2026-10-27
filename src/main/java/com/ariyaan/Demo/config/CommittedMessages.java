package com.ariyaan.Demo.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** Never announce a state transition which subsequently rolls back. Clients recover via profile polling. */
@Primary
@Component
public class CommittedMessages extends SimpMessagingTemplate {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CommittedMessages.class);
    private SimpMessagingTemplate local;
    private com.ariyaan.Demo.Service.MessagePreparation preparation;
    private boolean shared;
    public CommittedMessages(@Qualifier("brokerChannel") MessageChannel channel) { super(channel); }
    @org.springframework.beans.factory.annotation.Autowired
    public CommittedMessages(@Qualifier("brokerChannel") MessageChannel channel,
            @Qualifier("brokerMessagingTemplate") SimpMessagingTemplate local,
            com.ariyaan.Demo.Service.MessagePreparation preparation,
            @org.springframework.beans.factory.annotation.Value("${app.messaging.shared:false}") boolean shared) {
        this(channel);this.local=local;this.preparation=preparation;this.shared=shared;
        setMessageConverter(local.getMessageConverter());
    }
    @Override
    protected void doSend(String destination, Message<?> message) {
        Object payload=preparation==null?message.getPayload():preparation.prepare(destination,message.getPayload(),shared);
        if(shared)return; // Every node, including this one, dispatches from the committed database log.
        Runnable send=()->{ if(local==null) CommittedMessages.super.doSend(destination,message); else local.convertAndSend(destination,payload); };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { send.run(); }
                    catch (RuntimeException ex) { log.error("Committed ride event could not be delivered to {}",destination); }
                }
            });
        } else send.run();
    }
}
