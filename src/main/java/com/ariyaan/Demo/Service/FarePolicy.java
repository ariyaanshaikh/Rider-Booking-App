package com.ariyaan.Demo.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** All money used for collection and transfers is integer paise. */
public final class FarePolicy {
    private FarePolicy() {}
    public record Quote(long distanceMeters, long amountPaise, long businessPaise, long riderPaise) {}
    public static Quote quote(long meters) {
        if (meters <= 0) throw new IllegalArgumentException("Google route distance must be positive");
        long amount = BigDecimal.valueOf(meters).multiply(BigDecimal.valueOf(1900))
                .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).longValueExact();
        long commission = BigDecimal.valueOf(amount).multiply(new BigDecimal("0.10"))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new Quote(meters, amount, commission, amount - commission);
    }
}
