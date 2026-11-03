
package com.commercecore.controller;

import com.commercecore.dto.PaymentResponse;
import com.commercecore.service.PaymentService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final boolean simulationEnabled;

    public PaymentController(
            PaymentService paymentService,
            @Value("${commercecore.payments.simulation-enabled:false}")
            boolean simulationEnabled) {

        this.paymentService = paymentService;
        this.simulationEnabled = simulationEnabled;
    }

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

    private void requireSimulationEnabled() {

        if (!simulationEnabled) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Payment simulation is disabled"
            );
        }
    }

    // Criar pagamento pendente.
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

    // Consultar pagamento.
    @GetMapping
    public PaymentResponse getPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {

        return paymentService.getPayment(
            authenticatedUserId(jwt),
            orderId
        );
    }

    // Aprovar somente quando a simulacao estiver habilitada.
    @PostMapping("/approve")
    public PaymentResponse approvePayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {

        requireSimulationEnabled();

        return paymentService.approvePayment(
            authenticatedUserId(jwt),
            orderId
        );
    }

    // Rejeitar somente quando a simulacao estiver habilitada.
    @PostMapping("/reject")
    public PaymentResponse rejectPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long orderId) {

        requireSimulationEnabled();

        return paymentService.rejectPayment(
            authenticatedUserId(jwt),
            orderId
        );
    }
}
