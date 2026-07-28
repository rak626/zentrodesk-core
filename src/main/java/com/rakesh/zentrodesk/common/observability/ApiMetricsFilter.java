package com.rakesh.zentrodesk.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ApiMetricsFilter extends OncePerRequestFilter {

    private final ApplicationMetrics metrics;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            metrics.apiRequest(
                    scope(request.getRequestURI()),
                    request.getMethod(),
                    response.getStatus(),
                    Duration.ofNanos(System.nanoTime() - startedAt)
            );
        }
    }

    private String scope(String path) {
        if (path.startsWith("/api/app/")) {
            return "tenant";
        }
        if (path.startsWith("/api/platform/")) {
            return "platform";
        }
        if (path.startsWith("/actuator/")) {
            return "actuator";
        }
        return "other";
    }
}
