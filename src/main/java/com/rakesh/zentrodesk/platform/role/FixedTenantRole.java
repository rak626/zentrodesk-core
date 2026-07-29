package com.rakesh.zentrodesk.platform.role;

import java.util.Set;

import static com.rakesh.zentrodesk.platform.role.TenantPermissions.AUDIT_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.BOOKING_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.BOOKING_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.BOOKING_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.CONVERSATION_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.CONVERSATION_ASSIGN;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.CONVERSATION_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.CONVERSATION_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.CUSTOMER_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.CUSTOMER_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.CUSTOMER_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.DASHBOARD_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.DRIVER_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.DRIVER_DELETE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.DRIVER_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.DRIVER_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.LEAD_ASSIGN;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.LEAD_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.LEAD_DELETE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.LEAD_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.LEAD_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.NOTIFICATION_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.NOTIFICATION_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.PAYMENT_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.PAYMENT_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.PAYMENT_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.PROPERTY_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.PROPERTY_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.PROPERTY_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.QUOTATION_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.QUOTATION_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.QUOTATION_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.REPORT_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.REPORT_EXPORT;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.RESERVATION_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.RESERVATION_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.RESERVATION_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.ROLE_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.ROOM_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.ROOM_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.ROOM_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.TASK_CREATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.TASK_READ;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.TASK_UPDATE;
import static com.rakesh.zentrodesk.platform.role.TenantPermissions.TEAM_READ;

public enum FixedTenantRole {

    OWNER("Owner", "Full tenant administration and operational access", TenantPermissions.all()),
    MANAGER("Manager", "Operational management with read-only team access", Set.of(
            DASHBOARD_READ, CUSTOMER_READ, CUSTOMER_CREATE, CUSTOMER_UPDATE, LEAD_READ, LEAD_CREATE, LEAD_UPDATE,
            LEAD_DELETE, LEAD_ASSIGN, QUOTATION_READ, QUOTATION_CREATE, QUOTATION_UPDATE, BOOKING_READ,
            BOOKING_CREATE, BOOKING_UPDATE, PAYMENT_READ, PAYMENT_CREATE, PAYMENT_UPDATE, PROPERTY_READ,
            PROPERTY_CREATE, PROPERTY_UPDATE, ROOM_READ, ROOM_CREATE, ROOM_UPDATE, RESERVATION_READ,
            RESERVATION_CREATE, RESERVATION_UPDATE, DRIVER_READ, DRIVER_CREATE, DRIVER_UPDATE, DRIVER_DELETE,
            TASK_READ, TASK_CREATE, TASK_UPDATE, CONVERSATION_READ, CONVERSATION_CREATE, CONVERSATION_UPDATE,
            CONVERSATION_ASSIGN,
            NOTIFICATION_READ, NOTIFICATION_UPDATE, REPORT_READ, REPORT_EXPORT, AUDIT_READ, TEAM_READ, ROLE_READ
    )),
    SALES("Sales", "Customer, lead, quotation, and booking sales workflows", Set.of(
            DASHBOARD_READ, CUSTOMER_READ, CUSTOMER_CREATE, CUSTOMER_UPDATE, LEAD_READ, LEAD_CREATE, LEAD_UPDATE,
            LEAD_ASSIGN, QUOTATION_READ, QUOTATION_CREATE, QUOTATION_UPDATE, BOOKING_READ, BOOKING_CREATE,
            BOOKING_UPDATE, PAYMENT_READ, TASK_READ, TASK_CREATE, TASK_UPDATE, CONVERSATION_READ,
            CONVERSATION_CREATE, CONVERSATION_UPDATE, NOTIFICATION_READ, NOTIFICATION_UPDATE
    )),
    OPERATIONS("Operations", "Booking allocation, inventory, driver, and task operations", Set.of(
            DASHBOARD_READ, CUSTOMER_READ, BOOKING_READ, BOOKING_UPDATE, PROPERTY_READ, PROPERTY_CREATE,
            PROPERTY_UPDATE, ROOM_READ, ROOM_CREATE, ROOM_UPDATE, RESERVATION_READ, RESERVATION_CREATE,
            RESERVATION_UPDATE, DRIVER_READ, DRIVER_CREATE, DRIVER_UPDATE, DRIVER_DELETE, TASK_READ, TASK_CREATE,
            TASK_UPDATE, CONVERSATION_READ, CONVERSATION_CREATE, CONVERSATION_UPDATE,
            NOTIFICATION_READ, NOTIFICATION_UPDATE
    )),
    ACCOUNTS("Accounts", "Booking payment and financial reporting access", Set.of(
            DASHBOARD_READ, CUSTOMER_READ, BOOKING_READ, PAYMENT_READ, PAYMENT_CREATE, PAYMENT_UPDATE,
            CONVERSATION_READ, CONVERSATION_CREATE, CONVERSATION_UPDATE,
            NOTIFICATION_READ, NOTIFICATION_UPDATE, REPORT_READ, REPORT_EXPORT
    ));

    private final String displayName;
    private final String description;
    private final Set<String> permissions;

    FixedTenantRole(String displayName, String description, Set<String> permissions) {
        this.displayName = displayName;
        this.description = description;
        this.permissions = Set.copyOf(permissions);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Set<String> permissions() {
        return permissions;
    }
}
