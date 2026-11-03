
package com.commercecore.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Erros de regras de negocio: 400, 404, 409 etc.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        return buildError(
            ex.getStatusCode().value(),
            ex.getReason() != null
                ? ex.getReason()
                : "Request failed",
            request.getRequestURI()
        );
    }

    // Campos invalidos em DTOs com @Valid.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> fields = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors()
            .forEach(error -> fields.put(
                error.getField(),
                error.getDefaultMessage()
            ));

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now().toString());
        body.put("status", 400);
        body.put("error", "Bad Request");
        body.put("message", "Validation failed");
        body.put("path", request.getRequestURI());
        body.put("fields", fields);

        return ResponseEntity.badRequest().body(body);
    }

    // JSON malformado ou incompativel.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return buildError(
            400,
            "Invalid or malformed JSON request body",
            request.getRequestURI()
        );
    }

    // Parametros com tipos incorretos.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        return buildError(
            400,
            "Invalid value for parameter: " + ex.getName(),
            request.getRequestURI()
        );
    }

    // Conflitos de integridade no PostgreSQL.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        return buildError(
            409,
            "Operation conflicts with existing database records",
            request.getRequestURI()
        );
    }

    // Estrutura padrao da resposta JSON.
    private ResponseEntity<Map<String, Object>> buildError(
            int status,
            String message,
            String path) {

        HttpStatus httpStatus = HttpStatus.valueOf(status);

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", httpStatus.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);

        return ResponseEntity.status(status).body(body);
    }
}
