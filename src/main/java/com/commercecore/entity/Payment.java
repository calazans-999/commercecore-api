
package com.commercecore.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        unique = true
    )
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal amount;

    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
        name = "updated_at",
        nullable = false,
        insertable = false
    )
    private LocalDateTime updatedAt;

    // Construtor exigido pelo JPA.
    protected Payment() {
    }

    // Cria um pagamento vinculado a um pedido.
    public Payment(Order order) {

        if (order == null) {
            throw new IllegalArgumentException(
                "Order is required"
            );
        }

        if (order.getTotalAmount() == null ||
                order.getTotalAmount().signum() < 0) {

            throw new IllegalArgumentException(
                "Invalid order amount"
            );
        }

        this.order = order;
        this.amount = order.getTotalAmount();
        this.status = PaymentStatus.PENDING;
    }

    // Aprova um pagamento pendente.
    public void approve() {

        ensurePending();

        this.status = PaymentStatus.APPROVED;
    }

    // Rejeita um pagamento pendente.
    public void reject() {

        ensurePending();

        this.status = PaymentStatus.REJECTED;
    }

    // Impede processamento duplicado.
    private void ensurePending() {

        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                "Only pending payments can be processed"
            );
        }
    }

    // Atualiza a data sempre que o registro for alterado.
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
