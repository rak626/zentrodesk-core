package com.rakesh.zentrodesk.platform.user;

import com.rakesh.zentrodesk.platform.auth.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordResetRequest(
        @StrongPassword
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 12, maxLength = 100,
                pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$")
        String password
) {
}
