
package com.commercecore.service;

import com.commercecore.dto.PaymentResponse;

import com.commercecore.entity.Order;
import com.commercecore.entity.OrderStatus;
import com.commercecore.entity.Payment;
import com.commercecore.entity.PaymentStatus;

import com.commercecore.repository.OrderRepository;
import com.commercecore.repository.PaymentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository) {

        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    // ============================================================
    // CRIAR PAGAMENTO SIMULADO
    // ============================================================

    @Transactional
    public PaymentResponse createPayment(
            Long userId,
            Long orderId) {

        // Bloqueia o pedido para impedir criacoes simultaneas
        // e coordenar a operacao com o cancelamento.

        Order order = lockOrder(userId, orderId);

        ensureOrderPlaced(order);

        // Cada pedido pode possuir apenas um pagamento.
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Payment already exists for this order"
            );
        }

        Payment payment = new Payment(order);

        Payment savedPayment =
            paymentRepository.saveAndFlush(payment);

        return toResponse(savedPayment);
    }

    // ============================================================
    // APROVAR PAGAMENTO
    // ============================================================

    @Transactional
    public PaymentResponse approvePayment(
            Long userId,
            Long orderId) {

        // Mantemos a ordem de bloqueio: pedido primeiro,
        // pagamento depois.

        Order order = lockOrder(userId, orderId);

        ensureOrderPlaced(order);

        Payment payment = lockPayment(userId, orderId);

        ensurePaymentPending(payment);

        payment.approve();

        paymentRepository.saveAndFlush(payment);

        return toResponse(payment);
    }

    // ============================================================
    // REJEITAR PAGAMENTO
    // ============================================================

    @Transactional
    public PaymentResponse rejectPayment(
            Long userId,
            Long orderId) {

        Order order = lockOrder(userId, orderId);

        ensureOrderPlaced(order);

        Payment payment = lockPayment(userId, orderId);

        ensurePaymentPending(payment);

        payment.reject();

        paymentRepository.saveAndFlush(payment);

        return toResponse(payment);
    }

    // ============================================================
    // CONSULTAR PAGAMENTO
    // ============================================================

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(
            Long userId,
            Long orderId) {

        Payment payment = paymentRepository
            .findByOrderIdAndOrderUserId(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Payment not found"
            ));

        return toResponse(payment);
    }

    // ============================================================
    // BLOQUEAR PEDIDO
    // ============================================================

    private Order lockOrder(Long userId, Long orderId) {

        return orderRepository
            .findByIdAndUserIdForUpdate(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found"
            ));
    }

    // ============================================================
    // BLOQUEAR PAGAMENTO
    // ============================================================

    private Payment lockPayment(
            Long userId,
            Long orderId) {

        return paymentRepository
            .findByOrderIdAndUserIdForUpdate(orderId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Payment not found"
            ));
    }

    // ============================================================
    // VALIDAR STATUS DO PEDIDO
    // ============================================================

    private void ensureOrderPlaced(Order order) {

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Payments require a placed order"
            );
        }
    }

    // ============================================================
    // VALIDAR STATUS DO PAGAMENTO
    // ============================================================

    private void ensurePaymentPending(Payment payment) {

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Payment has already been processed"
            );
        }
    }

    // ============================================================
    // CONVERTER ENTIDADE PARA DTO
    // ============================================================

    private PaymentResponse toResponse(Payment payment) {

        return new PaymentResponse(
            payment.getId(),
            payment.getOrder().getId(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }
}
