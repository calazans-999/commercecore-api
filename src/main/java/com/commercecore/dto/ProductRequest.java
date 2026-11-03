package com.commercecore.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(

    @NotBlank(message = "Product name is required")
    @Size(max = 150)
    String name,

    String description,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00")
    @Digits(integer = 10, fraction = 2)
    BigDecimal price,

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0)
    Integer stockQuantity,

    @NotNull(message = "Category ID is required")
    @Positive
    Long categoryId

) {
}