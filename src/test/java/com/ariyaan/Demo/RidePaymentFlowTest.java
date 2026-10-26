package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.ariyaan.Demo.Service.*;
import com.ariyaan.Demo.Repository.*;
import com.ariyaan.Demo.Entity.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RidePaymentFlowTest {
    final RideDetailsRepository rides = mock(RideDetailsRepository.class);
    final RiderDemoRepository riders = mock(RiderDemoRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final RidePaymentStore payments = mock(RidePaymentStore.class);
    final InvoiceRepository invoices = mock(InvoiceRepository.class);
    final SimpMessagingTemplate messages = mock(SimpMessagingTemplate.class);
    final RideService service = new RideService(rides, mock(RideRequestRepository.class), mock(RiderMatchingService.class),
        messages, riders, invoices, users, payments);
    RideDetailsEntity fixture(String state) {
        var user = new User(); user.setId(5); user.setUserName("rider");
        var rider = new RiderDemoEntity(); rider.setId(9); rider.setUserId(5L);
        when(users.findByUserName("rider")).thenReturn(Optional.of(user));
        when(riders.findByUserId(5L)).thenReturn(Optional.of(rider));
        when(riders.findById(9L)).thenReturn(Optional.of(rider));
        var ride = new RideDetailsEntity(); ride.setRideId(42); ride.setAdminId(3);
        ride.setRiderId(9); ride.setRideOtp(1234); ride.setRideStatus(state); ride.setFare(361);
        when(rides.findByIdForUpdate(42)).thenReturn(Optional.of(ride));
        return ride;
    }
    @Test void otpDoesNotStartRideOrCreditRider() {
        var ride = fixture("ACCEPTED");
        assertTrue(service.startRide(42,9,1234,"rider"));
        assertEquals("AWAITING_PAYMENT", ride.getRideStatus());
        verifyNoInteractions(payments, invoices);
    }
    @Test void wrongOtpAndOtherRiderCannotChangeState() {
        var ride = fixture("ACCEPTED");
        assertThrows(RuntimeException.class, () -> service.startRide(42,9,9999,"rider"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> service.startRide(42,8,1234,"rider"));
        assertEquals("ACCEPTED", ride.getRideStatus());
    }
    @Test void unpaidRideCannotBeCompleted() {
        fixture("STARTED");
        when(payments.isCaptured(42)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.endRide(42,9,"rider"));
        verify(invoices, never()).save(any());
        verify(payments, never()).enqueueCompleted(anyInt());
    }
    @Test void completedRideQueuesTransferAndRepeatedCompletionIsHarmless() {
        var ride=fixture("STARTED");
        when(payments.isCaptured(42)).thenReturn(true);
        when(invoices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertTrue(service.endRide(42,9,"rider"));
        assertEquals("COMPLETED", ride.getRideStatus());
        assertTrue(service.endRide(42,9,"rider"));
        verify(payments, times(1)).enqueueCompleted(42);
        verify(invoices, times(1)).save(any());
    }
}
