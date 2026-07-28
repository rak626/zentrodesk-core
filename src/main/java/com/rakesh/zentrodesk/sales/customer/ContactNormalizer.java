package com.rakesh.zentrodesk.sales.customer;

import java.util.Locale;

public final class ContactNormalizer {

    private ContactNormalizer() {
    }

    public static String phone(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }

    public static String email(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
