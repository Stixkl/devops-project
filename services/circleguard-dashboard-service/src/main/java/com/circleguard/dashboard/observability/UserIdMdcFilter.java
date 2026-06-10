package com.circleguard.dashboard.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserIdMdcFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String MDC_USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            MDC.put(MDC_USER_ID_KEY, userId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (userId != null && !userId.isBlank()) {
                MDC.remove(MDC_USER_ID_KEY);
            }
        }
    }
}
