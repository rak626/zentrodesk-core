package com.rakesh.zentrodesk.operations.booking;

import com.rakesh.zentrodesk.common.ownership.OwnershipChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BookingOwnershipListener {

    private final BookingRepository bookingRepository;

    @EventListener
    void on(OwnershipChangedEvent event) {
        Map<UUID, Booking> bookings = new LinkedHashMap<>();
        if (event.leadId() != null) {
            bookingRepository.findAllByTenantIdAndLeadId(event.tenantId(), event.leadId())
                    .forEach(booking -> bookings.put(booking.getId(), booking));
        }
        if (event.quotationId() != null) {
            bookingRepository.findAllByTenantIdAndQuotationId(event.tenantId(), event.quotationId())
                    .forEach(booking -> bookings.put(booking.getId(), booking));
        }
        if (event.bookingId() != null) {
            bookingRepository.findByIdAndTenantId(event.bookingId(), event.tenantId())
                    .ifPresent(booking -> bookings.put(booking.getId(), booking));
        }
        bookings.values().forEach(booking -> booking.setAssignedUserId(event.ownerId()));
    }
}
