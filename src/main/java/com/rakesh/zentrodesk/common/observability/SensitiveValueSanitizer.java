package com.rakesh.zentrodesk.common.observability;

import java.util.regex.Pattern;

public final class SensitiveValueSanitizer {

    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(authorization|cookie|password|secret|token|email|phone|firstName|lastName|customer)"
                    + "[\"']?\\s*[:=]\\s*[\"']?[^,;\\r\\n]+"
    );

    private SensitiveValueSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = SENSITIVE_VALUE.matcher(value).replaceAll("$1=[REDACTED]");
        return sanitized.substring(0, Math.min(sanitized.length(), 255));
    }

    public static String safeErrorType(Throwable throwable) {
        return throwable == null ? "UnknownError" : throwable.getClass().getSimpleName();
    }
}
