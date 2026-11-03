package com.commercecore.service;

import com.commercecore.dto.RegisterRequest;
import com.commercecore.entity.User;
import com.commercecore.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Test
    void shouldRegisterCustomer() {
        RegisterRequest request = new RegisterRequest(
            "Test Customer",
            "customer@example.com",
            "SecurePass123!"
        );

        when(passwordEncoder.encode("SecurePass123!"))
            .thenReturn("$2a$12$exampleHash");

        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(request);

        assertEquals("Test Customer", response.name());
        assertEquals("customer@example.com", response.email());
        assertEquals("CUSTOMER", response.role());

        verify(passwordEncoder).encode("SecurePass123!");
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
            "Test Customer",
            "customer@example.com",
            "SecurePass123!"
        );

        when(userRepository.existsByEmailIgnoreCase(
            "customer@example.com"
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> authService.register(request)
        );

        assertEquals(
            HttpStatus.CONFLICT,
            exception.getStatusCode()
        );

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldNormalizeEmail() {
        RegisterRequest request = new RegisterRequest(
            "Test Customer",
            "  CUSTOMER@EXAMPLE.COM  ",
            "SecurePass123!"
        );

        when(passwordEncoder.encode(anyString()))
            .thenReturn("encoded-password");

        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(request);

        assertEquals(
            "customer@example.com",
            response.email()
        );
    }
}