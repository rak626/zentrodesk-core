package com.rakesh.zentrodesk.platform.role;

import com.rakesh.zentrodesk.analytics.dashboard.DashboardController;
import com.rakesh.zentrodesk.communication.conversation.api.ConversationMessageController;
import com.rakesh.zentrodesk.communication.notification.api.NotificationController;
import com.rakesh.zentrodesk.inventory.property.api.PropertyController;
import com.rakesh.zentrodesk.inventory.reservation.api.ReservationController;
import com.rakesh.zentrodesk.inventory.room.api.RoomController;
import com.rakesh.zentrodesk.operations.booking.api.BookingController;
import com.rakesh.zentrodesk.operations.driver.api.DriverController;
import com.rakesh.zentrodesk.operations.task.api.TaskController;
import com.rakesh.zentrodesk.platform.audit.AuditEventController;
import com.rakesh.zentrodesk.platform.invitation.InvitationController;
import com.rakesh.zentrodesk.platform.user.UserController;
import com.rakesh.zentrodesk.sales.lead.api.LeadController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedTenantRoleTest {

    @Test
    void definesFiveDistinctFixedTemplatesWithKnownPermissions() {
        assertEquals(5, FixedTenantRole.values().length);
        assertEquals(TenantPermissions.all(), FixedTenantRole.OWNER.permissions());

        for (FixedTenantRole role : FixedTenantRole.values()) {
            assertFalse(role.permissions().isEmpty());
            assertTrue(TenantPermissions.all().containsAll(role.permissions()));
        }
    }

    @Test
    void roleMatrixSeparatesRepresentativeAllowedAndForbiddenActions() {
        assertAllowed(FixedTenantRole.OWNER, TenantPermissions.TEAM_DELETE);
        assertAllowed(FixedTenantRole.MANAGER, TenantPermissions.PROPERTY_UPDATE);
        assertAllowed(FixedTenantRole.MANAGER, TenantPermissions.AUDIT_READ);
        assertForbidden(FixedTenantRole.MANAGER, TenantPermissions.TEAM_DELETE);
        assertAllowed(FixedTenantRole.SALES, TenantPermissions.LEAD_CREATE);
        assertForbidden(FixedTenantRole.SALES, TenantPermissions.PROPERTY_CREATE);
        assertForbidden(FixedTenantRole.SALES, TenantPermissions.AUDIT_READ);
        assertAllowed(FixedTenantRole.OPERATIONS, TenantPermissions.RESERVATION_UPDATE);
        assertForbidden(FixedTenantRole.OPERATIONS, TenantPermissions.PAYMENT_CREATE);
        assertAllowed(FixedTenantRole.ACCOUNTS, TenantPermissions.PAYMENT_CREATE);
        assertForbidden(FixedTenantRole.ACCOUNTS, TenantPermissions.LEAD_READ);
    }

    @Test
    void everyBusinessControllerEndpointHasAnAuthorizationRule() {
        List<Class<?>> controllers = List.of(
                DashboardController.class,
                LeadController.class,
                BookingController.class,
                PropertyController.class,
                RoomController.class,
                ReservationController.class,
                DriverController.class,
                TaskController.class,
                ConversationMessageController.class,
                NotificationController.class,
                UserController.class,
                RoleController.class,
                AuditEventController.class,
                InvitationController.class
        );

        for (Class<?> controller : controllers) {
            PreAuthorize classRule = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                PreAuthorize methodRule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                assertNotNull(methodRule == null ? classRule : methodRule,
                        () -> controller.getSimpleName() + "." + method.getName() + " has no authorization rule");
            }
        }
    }

    private void assertAllowed(FixedTenantRole role, String permission) {
        assertTrue(role.permissions().contains(permission), () -> role + " should allow " + permission);
    }

    private void assertForbidden(FixedTenantRole role, String permission) {
        assertFalse(role.permissions().contains(permission), () -> role + " should forbid " + permission);
    }
}
