
package com.commercecore.repository;

import com.commercecore.entity.Payment;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    // Verifica se um pedido ja possui pagamento.
    boolean existsByOrderId(Long orderId);

    // Busca o pagamento vinculado a um pedido.
    Optional<Payment> findByOrderId(Long orderId);

    // Busca pagamento somente se o pedido pertencer
    // ao usuario informado.
    Optional<Payment> findByOrderIdAndOrderUserId(
        Long orderId,
        Long userId
    );

    // Bloqueia o pagamento para processamento.
    // Evita duas operacoes simultaneas no mesmo registro.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Payment p
        WHERE p.order.id = :orderId
          AND p.order.user.id = :userId
        """)
    Optional<Payment> findByOrderIdAndUserIdForUpdate(
        @Param("orderId") Long orderId,
        @Param("userId") Long userId
    );
}
