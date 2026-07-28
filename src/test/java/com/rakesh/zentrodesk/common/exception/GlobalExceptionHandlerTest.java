package com.rakesh.zentrodesk.common.exception;

import com.rakesh.zentrodesk.common.api.ApiError;
import com.rakesh.zentrodesk.common.observability.ApplicationMetrics;
import com.rakesh.zentrodesk.common.web.CorrelationIdFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new ApplicationMetrics(new SimpleMeterRegistry())
    );

    @Test
    void returnsStableFieldErrorsAndCorrelationId() {
        Object target = new Object();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(target, "request");
        binding.addError(new FieldError(
                "request",
                "email",
                null,
                false,
                new String[]{"NotBlank"},
                null,
                "must not be blank"
        ));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, binding);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/auth/login");
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "request-123");

        ResponseEntity<ApiError> response = handler.handleValidation(exception, request);
        ApiError body = response.getBody();

        assertNotNull(body);
        assertEquals(400, body.status());
        assertEquals("VALIDATION_FAILED", body.code());
        assertEquals("request-123", body.correlationId());
        assertEquals("REQUIRED", body.fieldErrors().getFirst().code());
    }
}
