package com.commercecore.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {
}