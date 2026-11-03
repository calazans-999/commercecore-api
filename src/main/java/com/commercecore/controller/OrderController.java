
package com.commercecore.controller;

import com.commercecore.dto.OrderResponse;
import com.commercecore.service.OrderService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ============================================================
    // IDENTIFICAR CLIENTE AUTENTICADO
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
    // FINALIZAR COMPRA
    // ============================================================

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            @AuthenticationPrincipal Jwt jwt) {

        return orderService.checkout(
            authenticatedUserId(jwt)
        );
    }

    // ============================================================
    // CANCELAR PEDIDO
    // ============================================================

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        return orderService.cancelOrder(
            authenticatedUserId(jwt),
            id
        );
    }

    // ============================================================
    // LISTAR HISTORICO DE PEDIDOS
    // ============================================================

    @GetMapping
    public Page<OrderResponse> listOrders(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {

        return orderService.listOrders(
            authenticatedUserId(jwt),
            pageable
        );
    }

    // ============================================================
    // CONSULTAR PEDIDO POR ID
    // ============================================================

    @GetMapping("/{id}")
    public OrderResponse getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {

        return orderService.getOrder(
            authenticatedUserId(jwt),
            id
        );
    }
}
