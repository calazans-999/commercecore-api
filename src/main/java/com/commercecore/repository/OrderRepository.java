
package com.commercecore.repository;

import com.commercecore.entity.Order;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    // Lista os pedidos de um cliente.
    Page<Order> findByUserIdOrderByCreatedAtDesc(
        Long userId,
        Pageable pageable
    );

    // Consulta um pedido pertencente ao cliente.
    Optional<Order> findByIdAndUserId(
        Long id,
        Long userId
    );

    // Bloqueia o pedido durante o cancelamento.
    // Impede que duas transacoes cancelem o mesmo
    // pedido e devolvam o estoque duas vezes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.id = :orderId
          AND o.user.id = :userId
        """)
    Optional<Order> findByIdAndUserIdForUpdate(
        @Param("orderId") Long orderId,
        @Param("userId") Long userId
    );
}
