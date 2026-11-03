
package com.commercecore.service;

import com.commercecore.dto.LoginRequest;
import com.commercecore.dto.LoginResponse;
import com.commercecore.dto.RegisterRequest;
import com.commercecore.dto.RegisterResponse;
import com.commercecore.entity.User;
import com.commercecore.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String name = request.name().trim();
        String email = request.email().trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Email is already registered"
            );
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        User user = new User(name, email, passwordHash);

        User saved = userRepository.saveAndFlush(user);

        return new RegisterResponse(
            saved.getId(),
            saved.getName(),
            saved.getEmail(),
            saved.getRole().name()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = request.email().trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> invalidCredentials());

        if (!Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(
                    request.password(),
                    user.getPasswordHash())) {

            throw invalidCredentials();
        }

        return jwtService.generateToken(user);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Invalid email or password"
        );
    }
}
