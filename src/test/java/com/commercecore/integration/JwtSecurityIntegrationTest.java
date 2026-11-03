
package com.commercecore.integration;

import com.commercecore.entity.User;
import com.commercecore.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long createTestUser() {
        User user = new User(
            "JWT Test User",
            "jwt-" + UUID.randomUUID() + "@example.com",
            "test-only-password-hash"
        );

        return userRepository.saveAndFlush(user).getId();
    }

    private Long createAdmin() {
        Long id = createTestUser();

        jdbcTemplate.update(
            "UPDATE users SET role = 'ADMIN' WHERE id = ?",
            id
        );

        entityManager.clear();
        return id;
    }

    private String generateToken(
            JwtEncoder encoder,
            Long userId,
            Instant issuedAt,
            Instant expiresAt,
            String role) {

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("commercecore-api")
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("roles", List.of(role))
            .build();

        JwsHeader header = JwsHeader
            .with(MacAlgorithm.HS256)
            .build();

        return encoder.encode(
            JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    @Test
    void shouldAcceptValidAdminJwt() throws Exception {
        Long adminId = createAdmin();
        Instant now = Instant.now();

        String token = generateToken(
            jwtEncoder,
            adminId,
            now,
            now.plusSeconds(3600),
            "ADMIN"
        );

        String json = """
            {
                "name": "JWT-Valid-%s",
                "description": "Valid signed token test"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {
        Long userId = createTestUser();
        Instant now = Instant.now();

        String token = generateToken(
            jwtEncoder,
            userId,
            now.minusSeconds(3600),
            now.minusSeconds(600),
            "CUSTOMER"
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectJwtWithInvalidSignature()
            throws Exception {

        Long userId = createAdmin();

        byte[] wrongKeyBytes = new byte[32];

        new java.security.SecureRandom()
            .nextBytes(wrongKeyBytes);

        SecretKey wrongKey = new SecretKeySpec(
            wrongKeyBytes,
            "HmacSHA256"
        );

        JwtEncoder wrongEncoder = new NimbusJwtEncoder(
            new ImmutableSecret<>(wrongKey)
        );

        Instant now = Instant.now();

        String token = generateToken(
            wrongEncoder,
            userId,
            now,
            now.plusSeconds(3600),
            "ADMIN"
        );

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectDisabledUserWithValidJwt()
            throws Exception {

        Long userId = createTestUser();
        Instant now = Instant.now();

        String token = generateToken(
            jwtEncoder,
            userId,
            now,
            now.plusSeconds(3600),
            "CUSTOMER"
        );

        jdbcTemplate.update(
            "UPDATE users SET enabled = false WHERE id = ?",
            userId
        );

        entityManager.clear();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAdminAfterRoleDowngrade()
            throws Exception {

        Long adminId = createAdmin();
        Instant now = Instant.now();

        String token = generateToken(
            jwtEncoder,
            adminId,
            now,
            now.plusSeconds(3600),
            "ADMIN"
        );

        jdbcTemplate.update(
            "UPDATE users SET role = 'CUSTOMER' WHERE id = ?",
            adminId
        );

        entityManager.clear();

        String json = """
            {
                "name": "Role-Downgrade-%s",
                "description": "Should not be created"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isForbidden());
    }
}
