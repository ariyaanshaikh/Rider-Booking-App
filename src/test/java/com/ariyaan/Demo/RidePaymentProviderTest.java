package com.ariyaan.Demo;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ariyaan.Demo.Service.*;
import com.ariyaan.Demo.Repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RidePaymentProviderTest {
    final RidePaymentStore store=mock(RidePaymentStore.class);
    final RazorpayClient provider=mock(RazorpayClient.class);
    final RidePaymentService service=new RidePaymentService(store,provider,mock(SimpMessagingTemplate.class),
        mock(RideDetailsRepository.class),mock(UserRepository.class),mock(RiderDemoRepository.class));
    final RidePaymentStore.Payment payment=new RidePaymentStore.Payment(42,36100,3610,32490,
        "receipt","order_test","pay_test","CAPTURED","RECONCILE",null,"acc_test");
    final ObjectMapper json=new ObjectMapper();
    @Test void routeDisabledNeverCallsTransferApi() {
        when(provider.routeEnabled()).thenReturn(false);
        service.processTransfers();
        verifyNoInteractions(store);
        verify(provider,never()).post(anyString(),anyMap());
    }
    @Test void uncertainTransferIsReconciledInsteadOfResubmitted() throws Exception {
        when(provider.routeEnabled()).thenReturn(true);
        when(store.transferable()).thenReturn(List.of(payment));
        when(store.get(42)).thenReturn(payment);
        when(store.claimTransfer(payment)).thenReturn(false);
        when(provider.get("/payments/pay_test")).thenReturn(json.readTree(
            "{\"status\":\"captured\",\"amount\":36100,\"order_id\":\"order_test\",\"amount_refunded\":0}"));
        when(provider.get("/payments/pay_test/transfers")).thenReturn(json.readTree("{\"items\":[]}"));
        service.processTransfers();
        verify(provider,never()).post(anyString(),anyMap());
        verify(store).reconcileTransfer(42);
    }
    @Test void tamperedCallbackOrderIsRejectedBeforeProviderCalls() {
        when(store.get(42)).thenReturn(payment);
        assertThrows(IllegalArgumentException.class, () -> service.verify(42,"payer","order_other","pay_test","signature"));
        verifyNoInteractions(provider);
    }
    @Test void webhookSignatureIsCheckedBeforeDatabaseWork() {
        doThrow(new IllegalArgumentException("bad signature")).when(provider).verifyWebhook(anyString(),anyString());
        assertThrows(IllegalArgumentException.class, () -> service.webhook("{}","wrong","evt_test"));
        verifyNoInteractions(store);
    }
    @Test void providerTimeoutLeavesTransferForReconciliation() {
        when(provider.routeEnabled()).thenReturn(true);
        when(store.transferable()).thenReturn(List.of(payment));
        when(store.get(42)).thenReturn(payment);
        when(store.claimTransfer(payment)).thenReturn(true);
        when(provider.get(anyString())).thenThrow(new IllegalStateException("timeout"));
        service.processTransfers();
        verify(store).reconcileTransfer(42);
        verify(provider,never()).post(anyString(),anyMap());
    }
}
