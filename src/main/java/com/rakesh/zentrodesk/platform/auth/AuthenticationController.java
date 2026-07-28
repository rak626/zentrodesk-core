package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.platform.invitation.InvitationAcceptRequest;
import com.rakesh.zentrodesk.platform.invitation.InvitationDetailsResponse;
import com.rakesh.zentrodesk.platform.invitation.InvitationService;
import com.rakesh.zentrodesk.platform.invitation.InvitationTokenRequest;
import com.rakesh.zentrodesk.platform.password.PasswordResetCompleteRequest;
import com.rakesh.zentrodesk.platform.password.PasswordResetService;
import com.rakesh.zentrodesk.platform.password.PasswordResetStartRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final InvitationService invitationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Login successful", authenticationService.login(request), servletRequest.getRequestURI());
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Token refreshed successfully", authenticationService.refresh(request), servletRequest.getRequestURI());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        authenticationService.logout(request);
        return ApiResponse.success("Logout successful", null, servletRequest.getRequestURI());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser(HttpServletRequest servletRequest) {
        return ApiResponse.success("Current user retrieved successfully", authenticationService.currentUser(), servletRequest.getRequestURI());
    }

    @PostMapping("/invitations/validate")
    public ApiResponse<InvitationDetailsResponse> validateInvitation(
            @Valid @RequestBody InvitationTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("Invitation is valid", invitationService.validate(request), servletRequest.getRequestURI());
    }

    @PostMapping("/invitations/accept")
    public ApiResponse<Void> acceptInvitation(
            @Valid @RequestBody InvitationAcceptRequest request,
            HttpServletRequest servletRequest
    ) {
        invitationService.accept(request);
        return ApiResponse.success("Invitation accepted successfully", null, servletRequest.getRequestURI());
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetStartRequest request,
            HttpServletRequest servletRequest
    ) {
        passwordResetService.request(request);
        return ApiResponse.success("If the account is eligible, password reset instructions will be sent", null,
                servletRequest.getRequestURI());
    }

    @PostMapping("/password-reset/complete")
    public ApiResponse<Void> completePasswordReset(
            @Valid @RequestBody PasswordResetCompleteRequest request,
            HttpServletRequest servletRequest
    ) {
        passwordResetService.complete(request);
        return ApiResponse.success("Password reset successfully", null, servletRequest.getRequestURI());
    }
}
