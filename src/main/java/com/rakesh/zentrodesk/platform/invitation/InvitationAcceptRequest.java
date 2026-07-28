package com.rakesh.zentrodesk.platform.invitation;

import com.rakesh.zentrodesk.platform.auth.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationAcceptRequest(
        @NotBlank @Size(max = 200) String token,
        @StrongPassword
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 12, maxLength = 100,
                pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$")
        String password
) {
}
