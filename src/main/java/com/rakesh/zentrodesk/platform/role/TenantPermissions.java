package com.rakesh.zentrodesk.platform.role;

import java.util.Set;

public final class TenantPermissions {

    public static final String DASHBOARD_READ = "DASHBOARD_READ";
    public static final String CUSTOMER_READ = "CUSTOMER_READ";
    public static final String CUSTOMER_CREATE = "CUSTOMER_CREATE";
    public static final String CUSTOMER_UPDATE = "CUSTOMER_UPDATE";
    public static final String CUSTOMER_DELETE = "CUSTOMER_DELETE";
    public static final String LEAD_READ = "LEAD_READ";
    public static final String LEAD_CREATE = "LEAD_CREATE";
    public static final String LEAD_UPDATE = "LEAD_UPDATE";
    public static final String LEAD_DELETE = "LEAD_DELETE";
    public static final String LEAD_ASSIGN = "LEAD_ASSIGN";
    public static final String QUOTATION_READ = "QUOTATION_READ";
    public static final String QUOTATION_CREATE = "QUOTATION_CREATE";
    public static final String QUOTATION_UPDATE = "QUOTATION_UPDATE";
    public static final String BOOKING_READ = "BOOKING_READ";
    public static final String BOOKING_CREATE = "BOOKING_CREATE";
    public static final String BOOKING_UPDATE = "BOOKING_UPDATE";
    public static final String BOOKING_DELETE = "BOOKING_DELETE";
    public static final String PAYMENT_READ = "PAYMENT_READ";
    public static final String PAYMENT_CREATE = "PAYMENT_CREATE";
    public static final String PAYMENT_UPDATE = "PAYMENT_UPDATE";
    public static final String PROPERTY_READ = "PROPERTY_READ";
    public static final String PROPERTY_CREATE = "PROPERTY_CREATE";
    public static final String PROPERTY_UPDATE = "PROPERTY_UPDATE";
    public static final String ROOM_READ = "ROOM_READ";
    public static final String ROOM_CREATE = "ROOM_CREATE";
    public static final String ROOM_UPDATE = "ROOM_UPDATE";
    public static final String RESERVATION_READ = "RESERVATION_READ";
    public static final String RESERVATION_CREATE = "RESERVATION_CREATE";
    public static final String RESERVATION_UPDATE = "RESERVATION_UPDATE";
    public static final String DRIVER_READ = "DRIVER_READ";
    public static final String DRIVER_CREATE = "DRIVER_CREATE";
    public static final String DRIVER_UPDATE = "DRIVER_UPDATE";
    public static final String DRIVER_DELETE = "DRIVER_DELETE";
    public static final String TASK_READ = "TASK_READ";
    public static final String TASK_CREATE = "TASK_CREATE";
    public static final String TASK_UPDATE = "TASK_UPDATE";
    public static final String CONVERSATION_READ = "CONVERSATION_READ";
    public static final String CONVERSATION_CREATE = "CONVERSATION_CREATE";
    public static final String CONVERSATION_UPDATE = "CONVERSATION_UPDATE";
    public static final String NOTIFICATION_READ = "NOTIFICATION_READ";
    public static final String NOTIFICATION_UPDATE = "NOTIFICATION_UPDATE";
    public static final String REPORT_READ = "REPORT_READ";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";
    public static final String AUDIT_READ = "AUDIT_READ";
    public static final String TEAM_READ = "TEAM_READ";
    public static final String TEAM_CREATE = "TEAM_CREATE";
    public static final String TEAM_UPDATE = "TEAM_UPDATE";
    public static final String TEAM_DELETE = "TEAM_DELETE";
    public static final String TEAM_ASSIGN_ROLE = "TEAM_ASSIGN_ROLE";
    public static final String TEAM_RESET_PASSWORD = "TEAM_RESET_PASSWORD";
    public static final String ROLE_READ = "ROLE_READ";

    private static final Set<String> ALL = Set.of(
            DASHBOARD_READ,
            CUSTOMER_READ,
            CUSTOMER_CREATE,
            CUSTOMER_UPDATE,
            CUSTOMER_DELETE,
            LEAD_READ,
            LEAD_CREATE,
            LEAD_UPDATE,
            LEAD_DELETE,
            LEAD_ASSIGN,
            QUOTATION_READ,
            QUOTATION_CREATE,
            QUOTATION_UPDATE,
            BOOKING_READ,
            BOOKING_CREATE,
            BOOKING_UPDATE,
            BOOKING_DELETE,
            PAYMENT_READ,
            PAYMENT_CREATE,
            PAYMENT_UPDATE,
            PROPERTY_READ,
            PROPERTY_CREATE,
            PROPERTY_UPDATE,
            ROOM_READ,
            ROOM_CREATE,
            ROOM_UPDATE,
            RESERVATION_READ,
            RESERVATION_CREATE,
            RESERVATION_UPDATE,
            DRIVER_READ,
            DRIVER_CREATE,
            DRIVER_UPDATE,
            DRIVER_DELETE,
            TASK_READ,
            TASK_CREATE,
            TASK_UPDATE,
            CONVERSATION_READ,
            CONVERSATION_CREATE,
            CONVERSATION_UPDATE,
            NOTIFICATION_READ,
            NOTIFICATION_UPDATE,
            REPORT_READ,
            REPORT_EXPORT,
            AUDIT_READ,
            TEAM_READ,
            TEAM_CREATE,
            TEAM_UPDATE,
            TEAM_DELETE,
            TEAM_ASSIGN_ROLE,
            TEAM_RESET_PASSWORD,
            ROLE_READ
    );

    private TenantPermissions() {
    }

    public static Set<String> all() {
        return ALL;
    }

    public static String access(String permission) {
        return "hasAuthority('" + permission + "')";
    }
}
