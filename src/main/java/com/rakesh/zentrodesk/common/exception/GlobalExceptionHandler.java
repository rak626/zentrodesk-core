package com.rakesh.zentrodesk.common.exception;

import com.rakesh.zentrodesk.common.api.ApiError;
import com.rakesh.zentrodesk.common.api.FieldViolation;
import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import com.rakesh.zentrodesk.common.observability.SensitiveValueSanitizer;
import com.rakesh.zentrodesk.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApplicationMetrics metrics;

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/app/")) {
            metrics.tenantScopeMiss();
        }
        return error(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.atDebug()
                .addKeyValue("error_type", SensitiveValueSanitizer.safeErrorType(ex))
                .log("Conflicting request");
        return error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Request conflicts with existing data", request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED, ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED, "Authentication failed", request);
    }

    @ExceptionHandler(AuthenticationRateLimitException.class)
    ResponseEntity<ApiError> handleAuthenticationRateLimit(AuthenticationRateLimitException ex, HttpServletRequest request) {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.AUTHENTICATION_RATE_LIMITED,
                "Authentication temporarily unavailable",
                request
        );
    }

    @ExceptionHandler(SearchRateLimitException.class)
    ResponseEntity<ApiError> handleSearchRateLimit(SearchRateLimitException ex, HttpServletRequest request) {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.SEARCH_RATE_LIMITED,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        metrics.authorizationDenied();
        return error(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", errors, request);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ApiError> handleInvalidRequest(Exception ex, HttpServletRequest request) {
        log.atDebug()
                .addKeyValue("error_type", SensitiveValueSanitizer.safeErrorType(ex))
                .log("Invalid request");
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.atError()
                .addKeyValue("error_type", SensitiveValueSanitizer.safeErrorType(ex))
                .log("Unhandled request failure");
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        return error(status, code, message, List.of(), request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<FieldViolation> fieldErrors,
            HttpServletRequest request
    ) {
        Object correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                code.name(),
                message,
                request.getRequestURI(),
                correlationId == null ? null : correlationId.toString(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }

    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), fieldCode(error.getCode()), error.getDefaultMessage());
    }

    private String fieldCode(String constraint) {
        if (constraint == null) {
            return "INVALID_VALUE";
        }
        return switch (constraint) {
            case "Email", "Pattern" -> "INVALID_FORMAT";
            case "NotBlank", "NotEmpty", "NotNull" -> "REQUIRED";
            case "Size" -> "INVALID_SIZE";
            default -> "INVALID_VALUE";
        };
    }
}
