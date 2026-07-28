package com.rakesh.zentrodesk.communication.notification;

import com.rakesh.zentrodesk.communication.notification.api.NotificationService;
import com.rakesh.zentrodesk.inventory.reservation.ReservationChangedEvent;
import com.rakesh.zentrodesk.operations.assignment.AssignmentNotificationEvent;
import com.rakesh.zentrodesk.operations.booking.PaymentRecordedEvent;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.lead.FollowUpScheduledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OperationalNotificationListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void followUpScheduled(FollowUpScheduledEvent event) {
        create(
                event.tenantId(),
                event.ownerId(),
                "FOLLOW_UP:" + event.followUpId(),
                "FOLLOW_UP",
                "Follow-up scheduled",
                "A lead follow-up is ready in your queue.",
                "/leads?followUp=" + event.followUpId(),
                "FOLLOW_UP",
                event.followUpId()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void assignmentChanged(AssignmentNotificationEvent event) {
        if (!"OPERATIONS".equals(event.audience())) {
            return;
        }
        operationsRecipients(event.tenantId()).forEach(recipientId -> create(
                event.tenantId(),
                recipientId,
                "ASSIGNMENT:" + event.assignmentId() + ":" + event.eventType(),
                "ASSIGNMENT",
                "Driver assignment " + event.eventType().name().toLowerCase(),
                "A booking driver assignment changed.",
                "/drivers?assignment=" + event.assignmentId(),
                "DRIVER_ASSIGNMENT",
                event.assignmentId()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void paymentRecorded(PaymentRecordedEvent event) {
        paymentRecipients(event.tenantId()).forEach(recipientId -> create(
                event.tenantId(),
                recipientId,
                "PAYMENT:" + event.paymentId() + ":" + event.status(),
                "PAYMENT",
                "Payment " + event.status().name().toLowerCase(),
                "Payment activity was recorded for booking " + event.bookingNumber() + ".",
                "/bookings?booking=" + event.bookingId(),
                "PAYMENT",
                event.paymentId()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reservationChanged(ReservationChangedEvent event) {
        operationsRecipients(event.tenantId()).forEach(recipientId -> create(
                event.tenantId(),
                recipientId,
                "RESERVATION:" + event.reservationId() + ":" + event.status(),
                "RESERVATION",
                "Reservation " + event.status().name().toLowerCase(),
                "Room inventory changed for a booking.",
                "/availability?reservation=" + event.reservationId(),
                "RESERVATION",
                event.reservationId()
        ));
    }

    private Set<UUID> operationsRecipients(UUID tenantId) {
        return Set.copyOf(userRepository.findActiveOperationsNotificationRecipientIds(tenantId));
    }

    private Set<UUID> paymentRecipients(UUID tenantId) {
        return Set.copyOf(userRepository.findActivePaymentNotificationRecipientIds(tenantId));
    }

    private void create(
            UUID tenantId,
            UUID recipientId,
            String eventKey,
            String category,
            String title,
            String message,
            String deepLink,
            String sourceType,
            UUID sourceId
    ) {
        notificationService.createIdempotent(
                tenantId,
                recipientId,
                eventKey,
                category,
                title,
                message,
                deepLink,
                sourceType,
                sourceId
        );
    }
}
