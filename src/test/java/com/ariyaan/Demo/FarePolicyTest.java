package com.ariyaan.Demo;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.ariyaan.Demo.Service.FarePolicy;

class FarePolicyTest {
    @Test void matchesDocumentExamples() {
        var quote = FarePolicy.quote(19000);
        assertEquals(36100, quote.amountPaise());
        assertEquals(3610, quote.businessPaise());
        assertEquals(32490, quote.riderPaise());
        assertEquals(34200, FarePolicy.quote(20000).riderPaise());
        assertEquals(342000, FarePolicy.quote(200000).riderPaise());
    }
    @Test void roundsPaiseWithoutLosingMoney() {
        for (long meters = 1; meters < 10000; meters++) {
            var quote = FarePolicy.quote(meters);
            assertEquals(quote.amountPaise(), quote.businessPaise() + quote.riderPaise());
        }
    }
    @Test void rejectsInvalidDistance() {
        assertThrows(IllegalArgumentException.class, () -> FarePolicy.quote(0));
        assertThrows(IllegalArgumentException.class, () -> FarePolicy.quote(-1));
    }
}
