package com.rakesh.zentrodesk.communication.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ConversationAssignmentId.class)
@Table(
        name = "conversation_assignments",
        indexes = {
                @Index(
                        name = "idx_conversation_assignment_user",
                        columnList = "tenant_id, user_id, assigned_at"
                )
        }
)
public class ConversationAssignment {

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 20)
    private ConversationAssignmentRole role;

    @Column(name = "assigned_by_user_id", nullable = false)
    private UUID assignedByUserId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
}
