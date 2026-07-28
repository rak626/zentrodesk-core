package com.rakesh.zentrodesk.operations.booking;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRecordedEvent(
        UUID tenantId,
        UUID paymentId,
        UUID bookingId,
        String bookingNumber,
        BigDecimal amount,
        PaymentStatus status
) {
}
