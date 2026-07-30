package com.rakesh.zentrodesk.platform.audit;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/ownership-history")
class OwnershipHistoryController {

    private final OwnershipHistoryService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEAD_READ', 'QUOTATION_READ', 'BOOKING_READ', 'CONVERSATION_READ')")
    ApiResponse<List<OwnershipHistoryResponse>> findAll(
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID quotationId,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID conversationId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(
                "Ownership history retrieved successfully",
                service.findAll(leadId, quotationId, bookingId, conversationId),
                servletRequest.getRequestURI()
        );
    }
}
