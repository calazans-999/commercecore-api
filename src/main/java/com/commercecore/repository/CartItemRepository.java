package com.commercecore.repository;

import com.commercecore.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository
        extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(
        Long cartId,
        Long productId
    );

    Optional<CartItem> findByIdAndCartId(
        Long id,
        Long cartId
    );
}