
package com.commercecore.service;

import com.commercecore.dto.AddCartItemRequest;
import com.commercecore.dto.CartItemResponse;
import com.commercecore.dto.CartResponse;
import com.commercecore.dto.UpdateCartItemRequest;

import com.commercecore.entity.Cart;
import com.commercecore.entity.CartItem;
import com.commercecore.entity.Product;
import com.commercecore.entity.User;

import com.commercecore.repository.CartItemRepository;
import com.commercecore.repository.CartRepository;
import com.commercecore.repository.ProductRepository;
import com.commercecore.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {

        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(
            Long userId,
            AddCartItemRequest request) {

        Product product = findAvailableProduct(
            request.productId()
        );

        Cart cart = getOrCreateCart(userId);

        CartItem item = cartItemRepository
            .findByCartIdAndProductId(
                cart.getId(),
                product.getId()
            )
            .orElse(null);

        if (item == null) {
            validateQuantity(product, request.quantity());

            cart.addItem(
                new CartItem(product, request.quantity())
            );

        } else {
            int newQuantity;

            try {
                newQuantity = Math.addExact(
                    item.getQuantity(),
                    request.quantity()
                );
            } catch (ArithmeticException exception) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity is too large"
                );
            }

            validateQuantity(product, newQuantity);
            item.setQuantity(newQuantity);
        }

        cart.touch();
        cartRepository.saveAndFlush(cart);

        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(
            Long userId,
            Long itemId,
            UpdateCartItemRequest request) {

        Cart cart = findCart(userId);

        CartItem item = findOwnedItem(
            cart.getId(),
            itemId
        );

        Product product = findAvailableProduct(
            item.getProduct().getId()
        );

        validateQuantity(product, request.quantity());

        item.setQuantity(request.quantity());
        cart.touch();

        cartRepository.saveAndFlush(cart);

        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(
            Long userId,
            Long itemId) {

        Cart cart = findCart(userId);

        CartItem item = findOwnedItem(
            cart.getId(),
            itemId
        );

        // Ensure the entity is removed from the
        // managed collection, allowing orphanRemoval.
        cart.getItems().removeIf(
            current -> current.getId().equals(item.getId())
        );

        item.setCart(null);
        cart.touch();

        cartRepository.saveAndFlush(cart);

        return toResponse(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserId(userId)
            .ifPresent(cart -> {
                cart.clearItems();
                cart.touch();
                cartRepository.saveAndFlush(cart);
            });
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
            .orElseGet(() -> {

                User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                        new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "User not found"
                        )
                    );

                return cartRepository.saveAndFlush(
                    new Cart(user)
                );
            });
    }

    private Cart findCart(Long userId) {
        return cartRepository.findByUserId(userId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cart not found"
                )
            );
    }

    private CartItem findOwnedItem(
            Long cartId,
            Long itemId) {

        return cartItemRepository
            .findByIdAndCartId(itemId, cartId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cart item not found"
                )
            );
    }

    private Product findAvailableProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
                )
            );

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Product is inactive"
            );
        }

        return product;
    }

    private void validateQuantity(
            Product product,
            Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Quantity must be greater than zero"
            );
        }

        if (quantity > product.getStockQuantity()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Insufficient stock"
            );
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems()
            .stream()
            .map(item -> {

                Product product = item.getProduct();

                BigDecimal subtotal = product.getPrice()
                    .multiply(
                        BigDecimal.valueOf(item.getQuantity())
                    );

                return new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    item.getQuantity(),
                    subtotal
                );
            })
            .toList();

        int totalItems = items.stream()
            .mapToInt(CartItemResponse::quantity)
            .sum();

        BigDecimal total = items.stream()
            .map(CartItemResponse::subtotal)
            .reduce(
                BigDecimal.ZERO,
                BigDecimal::add
            );

        return new CartResponse(
            cart.getId(),
            cart.getUser().getId(),
            items,
            totalItems,
            total
        );
    }
}
