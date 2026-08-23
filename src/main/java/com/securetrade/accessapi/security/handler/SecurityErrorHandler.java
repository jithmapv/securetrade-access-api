package com.securetrade.accessapi.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrade.accessapi.common.enums.ErrorCode;
import com.securetrade.accessapi.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String ACCESS_DENIED_TYPE =
            "https://api.securetrade.com/errors/access-denied";

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        // Return 401 error
        writeError(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required to access this resource");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {

        // Return 403 error
        writeError(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "User is not authorized to access this resource");
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                ACCESS_DENIED_TYPE,
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                ErrorCode.ACCESS_DENIED.name(),
                Instant.now(),
                null);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
