package com.rakesh.zentrodesk.communication.conversation.api;

import com.rakesh.zentrodesk.common.exception.BadRequestException;
import com.rakesh.zentrodesk.communication.conversation.ConversationAssignmentRepository;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessage;
import com.rakesh.zentrodesk.communication.conversation.ConversationMessageRepository;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversation;
import com.rakesh.zentrodesk.communication.conversation.CustomerConversationRepository;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfiguration;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConfigurationRepository;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppConnectionStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppFundingStatus;
import com.rakesh.zentrodesk.communication.whatsapp.WhatsAppOutboxAccess;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import com.rakesh.zentrodesk.platform.user.UserRepository;
import com.rakesh.zentrodesk.sales.customer.CustomerRepository;
import com.rakesh.zentrodesk.sales.lead.LeadRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerConversationServiceTest {

    private final CustomerConversationRepository conversationRepository =
            mock(CustomerConversationRepository.class);
    private final ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
    private final WhatsAppConfigurationRepository configurationRepository =
            mock(WhatsAppConfigurationRepository.class);
    private final WhatsAppOutboxAccess outboxAccess = mock(WhatsAppOutboxAccess.class);
    private final CurrentTenant currentTenant = mock(CurrentTenant.class);
    private final CustomerConversationService service = new CustomerConversationService(
            conversationRepository,
            messageRepository,
            mock(ConversationAssignmentRepository.class),
            mock(CustomerRepository.class),
            mock(LeadRepository.class),
            mock(UserRepository.class),
            configurationRepository,
            outboxAccess,
            currentTenant,
            mock(ApplicationEventPublisher.class)
    );

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CustomerConversation conversation = CustomerConversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .phone("919000000000")
                .lastInboundAt(Instant.now().minus(25, ChronoUnit.HOURS))
                .build();
        WhatsAppConfiguration configuration = WhatsAppConfiguration.builder()
                .tenantId(tenantId)
                .phoneNumberId("phone-number")
                .fundingStatus(WhatsAppFundingStatus.FUNDED)
                .tokenHealthy(true)
                .status(WhatsAppConnectionStatus.CONNECTED)
                .build();
        when(currentTenant.id()).thenReturn(tenantId);
        when(currentTenant.userId()).thenReturn(userId);
        when(conversationRepository.findByIdAndTenantId(conversationId, tenantId))
                .thenReturn(Optional.of(conversation));
        when(configurationRepository.findByTenantId(tenantId)).thenReturn(Optional.of(configuration));
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            return message;
        });
    }

    @Test
    void freeformMessageRemainsBlockedOutsideCustomerServiceWindow() {
        assertThrows(
                BadRequestException.class,
                () -> service.send(conversationId, new ConversationSendRequest("Late freeform reply"))
        );
    }
}
