
package com.commercecore.controller;

import com.commercecore.dto.AddCartItemRequest;
import com.commercecore.dto.CartResponse;
import com.commercecore.dto.UpdateCartItemRequest;

import com.commercecore.service.CartService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
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

    @GetMapping
    public CartResponse getCart(
            @AuthenticationPrincipal Jwt jwt) {

        return cartService.getCart(
            authenticatedUserId(jwt)
        );
    }

    @PostMapping("/items")
    public CartResponse addItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddCartItemRequest request) {

        return cartService.addItem(
            authenticatedUserId(jwt),
            request
        );
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        return cartService.updateItem(
            authenticatedUserId(jwt),
            itemId,
            request
        );
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long itemId) {

        return cartService.removeItem(
            authenticatedUserId(jwt),
            itemId
        );
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(
            @AuthenticationPrincipal Jwt jwt) {

        cartService.clearCart(
            authenticatedUserId(jwt)
        );
    }
}
