
package com.commercecore.service;

import com.commercecore.dto.OrderItemResponse;
import com.commercecore.dto.OrderResponse;

import com.commercecore.entity.Cart;
import com.commercecore.entity.CartItem;
import com.commercecore.entity.Order;
import com.commercecore.entity.OrderItem;
import com.commercecore.entity.Product;
import com.commercecore.entity.User;

import com.commercecore.repository.CartRepository;
import com.commercecore.repository.OrderRepository;
import com.commercecore.repository.ProductRepository;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            ProductRepository productRepository,
            EntityManager entityManager) {

        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public OrderResponse checkout(Long userId) {

        // 1. Bloqueia o carrinho durante o checkout.
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cart not found"
            ));

        // 2. Verifica se o carrinho possui produtos.
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot checkout an empty cart"
            );
        }

        // 3. Processa produtos em ordem de ID.
        List<CartItem> cartItems = cart.getItems()
            .stream()
            .sorted(Comparator.comparing(
                item -> item.getProduct().getId()
            ))
            .toList();

        User user = cart.getUser();
        Order order = new Order(user);

        // 4. Valida e bloqueia cada produto.
        for (CartItem cartItem : cartItems) {

            Long productId = cartItem.getProduct().getId();
            int quantity = cartItem.getQuantity();

            Product product = productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
                ));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is inactive: " + productId
                );
            }

            if (product.getStockQuantity() == null ||
                    product.getStockQuantity() < quantity) {

                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient stock for product: " + productId
                );
            }

            // 5. Registra nome, preco e quantidade da compra.
            order.addItem(new OrderItem(product, quantity));

            // 6. Desconta o estoque.
            product.setStockQuantity(
                product.getStockQuantity() - quantity
            );
        }

        // 7. Salva o pedido com seus itens.
        Order savedOrder = orderRepository.saveAndFlush(order);

        // 8. Carrega os campos gerados pelo PostgreSQL.
        entityManager.refresh(savedOrder);

        // 9. Esvazia o carrinho.
        cart.clearItems();
        cart.touch();

        cartRepository.saveAndFlush(cart);

        // 10. Retorna o pedido criado.
        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {

        Order order = orderRepository
            .findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found"
            ));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(
            Long userId,
            Pageable pageable) {

        return orderRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
    }

    private OrderResponse toResponse(Order order) {

        List<OrderItemResponse> items = order.getItems()
            .stream()
            .map(item -> new OrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
            ))
            .toList();

        return new OrderResponse(
            order.getId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            items
        );
    }
}
