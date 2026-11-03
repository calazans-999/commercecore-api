
package com.commercecore.service;

import com.commercecore.dto.OrderItemResponse;
import com.commercecore.dto.OrderResponse;

import com.commercecore.entity.Cart;
import com.commercecore.entity.CartItem;
import com.commercecore.entity.Order;
import com.commercecore.entity.OrderItem;
import com.commercecore.entity.OrderStatus;
import com.commercecore.entity.Product;
import com.commercecore.entity.User;
import com.commercecore.entity.Payment;
import com.commercecore.entity.PaymentStatus;
import com.commercecore.repository.PaymentRepository;

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
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;


    public OrderService(
        OrderRepository orderRepository,
        CartRepository cartRepository,
        ProductRepository productRepository,
        PaymentRepository paymentRepository,
        EntityManager entityManager) {

    this.orderRepository = orderRepository;
    this.cartRepository = cartRepository;
    this.productRepository = productRepository;
    this.paymentRepository = paymentRepository;
    this.entityManager = entityManager;
}


    // ============================================================
    // CHECKOUT - FINALIZAR COMPRA
    // ============================================================

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

    // ============================================================
    // CANCELAMENTO DE PEDIDOS
    // ============================================================


@Transactional
public OrderResponse cancelOrder(Long userId, Long orderId) {

    // 1. Bloqueia o pedido e verifica a propriedade.
    Order order = orderRepository
        .findByIdAndUserIdForUpdate(orderId, userId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Order not found"
        ));

    // 2. Impede o cancelamento duplicado.
    if (order.getStatus() != OrderStatus.PLACED) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Only placed orders can be cancelled"
        );
    }

    // 3. Bloqueia o pagamento existente, se houver.
    Payment payment = paymentRepository
        .findByOrderIdAndUserIdForUpdate(orderId, userId)
        .orElse(null);

    // 4. Impede cancelar pedidos com pagamento aprovado.
    if (payment != null &&
            payment.getStatus() == PaymentStatus.APPROVED) {

        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Approved payments require a refund before cancellation"
        );
    }

    // 5. Ordena os itens para manter a ordem dos bloqueios.
    List<OrderItem> items = order.getItems()
        .stream()
        .sorted(Comparator.comparing(
            item -> item.getProduct().getId()
        ))
        .toList();

    // 6. Devolve os produtos ao estoque.
    for (OrderItem item : items) {

        Long productId = item.getProduct().getId();

        Product product = productRepository
            .findByIdForUpdate(productId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Product not found: " + productId
            ));

        Integer currentStock = product.getStockQuantity();
        Integer quantityToRestore = item.getQuantity();

        if (currentStock == null ||
                quantityToRestore == null ||
                quantityToRestore <= 0) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Invalid stock data for product: " + productId
            );
        }

        int restoredStock;

        try {
            restoredStock = Math.addExact(
                currentStock,
                quantityToRestore
            );
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Stock quantity overflow: " + productId
            );
        }

        product.setStockQuantity(restoredStock);
    }

    // 7. Rejeita um pagamento que ainda esteja pendente.
    if (payment != null &&
            payment.getStatus() == PaymentStatus.PENDING) {

        payment.reject();
        paymentRepository.saveAndFlush(payment);
    }

    // 8. Cancela o pedido.
    order.cancel();

    // 9. Persiste o novo status.
    orderRepository.saveAndFlush(order);

    // As alterações de estoque e pagamento serão confirmadas
    // junto com o cancelamento ao final da transação.
    return toResponse(order);
}


    // ============================================================
    // CONSULTAR PEDIDO POR ID
    // ============================================================

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

    // ============================================================
    // LISTAR HISTORICO DE PEDIDOS
    // ============================================================

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(
            Long userId,
            Pageable pageable) {

        return orderRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
    }

    // ============================================================
    // CONVERTER ENTIDADE PARA DTO
    // ============================================================

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
