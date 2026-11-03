package com.commercecore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
    Long id,
    String name,
    String description,
    BigDecimal price,
    Integer stockQuantity,
    Boolean active,
    Long categoryId,
    String categoryName,
    LocalDateTime createdAt
) {
}