
package com.commercecore.dto;

import com.commercecore.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    Long id,
    Long orderId,
    PaymentStatus status,
    BigDecimal amount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
