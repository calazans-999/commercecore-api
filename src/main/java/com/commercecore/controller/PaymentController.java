
package com.commercecore.controller;

import com.commercecore.dto.PaymentResponse;
import com.commercecore.service.PaymentService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ============================================================
    // IDENTIFICAR USUARIO AUTENTICADO
    // ============================================================

    private Long authenticatedUserId(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required"
            );
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid token subject"
            );
        }
    }

    // ============================================================
    // CRIAR PAGAMENTO SIMULADO
    // ============================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {

        return paymentService.createPayment(
            authenticatedUserId(jwt),
            orderId
        );
    }

    // ============================================================
    // CONSULTAR PAGAMENTO
    // ============================================================

    @GetMapping
    public PaymentResponse getPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {

        return paymentService.getPayment(
            authenticatedUserId(jwt),
            orderId
        );
    }

    // ============================================================
    // APROVAR PAGAMENTO SIMULADO
    // ============================================================

    @PostMapping("/approve")
    public PaymentResponse approvePayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {

        return paymentService.approvePayment(
            authenticatedUserId(jwt),
            orderId
        );
    }

    // ============================================================
    // REJEITAR PAGAMENTO SIMULADO
    // ============================================================

    @PostMapping("/reject")
    public PaymentResponse rejectPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {

        return paymentService.rejectPayment(
            authenticatedUserId(jwt),
            orderId
        );
    }
}
