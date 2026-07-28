package com.rakesh.zentrodesk.platform.audit;

import com.rakesh.zentrodesk.common.web.CorrelationIds;
import com.rakesh.zentrodesk.iam.principal.CurrentUser;
import com.rakesh.zentrodesk.platform.tenant.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditMutationAspect {

    private final AuditEventService auditEventService;
    private final CurrentTenant currentTenant;
    private final PlatformTransactionManager transactionManager;

    @Around("@annotation(com.rakesh.zentrodesk.platform.audit.AuditMutation)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        AuditMutation auditMutation = ((MethodSignature) joinPoint.getSignature()).getMethod()
                .getAnnotation(AuditMutation.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            return transactionTemplate.execute(status -> {
                try {
                    Object result = joinPoint.proceed();
                    UUID entityId = resolveEntityId(result, joinPoint.getArgs());
                    auditEventService.record(
                            currentTenant.id(),
                            currentActorId(),
                            auditMutation.entityType().toUpperCase(),
                            entityId,
                            auditMutation.action().toUpperCase(),
                            CorrelationIds.currentOrGenerated(),
                            beforeSummary(auditMutation.action(), entityId),
                            afterSummary(entityId)
                    );
                    return result;
                } catch (Throwable exception) {
                    status.setRollbackOnly();
                    throw new AuditedInvocationException(exception);
                }
            });
        } catch (AuditedInvocationException exception) {
            throw exception.getCause();
        }
    }

    private UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof CurrentUser currentUser && currentUser.isTenantUser()) {
            return currentUser.id();
        }
        return currentTenant.userId();
    }

    private UUID resolveEntityId(Object result, Object[] arguments) {
        UUID resultId = invokeUuidAccessor(result, "id");
        if (resultId == null) {
            resultId = invokeUuidAccessor(result, "getId");
        }
        if (resultId != null) {
            return resultId;
        }
        for (Object argument : arguments) {
            if (argument instanceof UUID id) {
                return id;
            }
        }
        return null;
    }

    private UUID invokeUuidAccessor(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof UUID id ? id : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String beforeSummary(String action, UUID entityId) {
        if ("CREATE".equalsIgnoreCase(action)) {
            return null;
        }
        return entityId == null ? "{\"state\":\"EXISTING\"}"
                : "{\"state\":\"EXISTING\",\"entityId\":\"" + entityId + "\"}";
    }

    private String afterSummary(UUID entityId) {
        return entityId == null ? "{\"outcome\":\"SUCCESS\"}"
                : "{\"outcome\":\"SUCCESS\",\"entityId\":\"" + entityId + "\"}";
    }

    private static final class AuditedInvocationException extends RuntimeException {

        private AuditedInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
