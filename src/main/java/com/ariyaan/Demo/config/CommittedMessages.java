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
    public CommittedMessages(@Qualifier("brokerChannel") MessageChannel channel) { super(channel); }
    @Override
    protected void doSend(String destination, Message<?> message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { CommittedMessages.super.doSend(destination,message); }
                    catch (RuntimeException ex) { log.error("Committed ride event could not be delivered to {}",destination); }
                }
            });
        } else super.doSend(destination,message);
    }
}
