
package com.commercecore.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SecurityErrorHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // HTTP 401 - Usuario nao autenticado.
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {

        writeError(
            request,
            response,
            HttpStatus.UNAUTHORIZED,
            "Authentication required"
        );
    }

    // HTTP 403 - Usuario autenticado sem permissao.
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception)
            throws IOException {

        writeError(
            request,
            response,
            HttpStatus.FORBIDDEN,
            "Access denied"
        );
    }

    // Gera uma resposta JSON padronizada.
    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message)
            throws IOException {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());

        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        objectMapper.writeValue(
            response.getOutputStream(),
            body
        );
    }
}
