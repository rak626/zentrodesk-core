package com.rakesh.zentrodesk.platform.auth;

import com.rakesh.zentrodesk.common.api.ApiResponse;
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
}
