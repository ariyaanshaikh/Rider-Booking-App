package com.ariyaan.Demo;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.*;
import com.ariyaan.Demo.config.CommittedMessages;

class CommittedMessagesTest {
    final MessageChannel channel=mock(MessageChannel.class);
    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }
    @Test void messagesArePublishedOnlyAfterCommit() {
        when(channel.send(any())).thenReturn(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        new CommittedMessages(channel).convertAndSend("/topic/user/3","accepted");
        verifyNoInteractions(channel);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(channel).send(any());
    }
    @Test void rollbackDoesNotPublish() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        new CommittedMessages(channel).convertAndSend("/topic/user/3","accepted");
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(channel);
    }
}
