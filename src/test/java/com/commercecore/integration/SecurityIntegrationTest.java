
package com.commercecore.integration;

import com.commercecore.entity.User;
import com.commercecore.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String categoryJson() {
        return """
            {
                "name": "Security-%s",
                "description": "Security integration test"
            }
            """.formatted(UUID.randomUUID());
    }

    @Test
    void shouldRejectUnauthenticatedCategoryCreation()
            throws Exception {

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCustomerCreatingCategory()
            throws Exception {

        mockMvc.perform(post("/api/v1/categories")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_CUSTOMER")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminCreatingCategory()
            throws Exception {

        mockMvc.perform(post("/api/v1/categories")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void shouldAllowPublicCategoryList()
            throws Exception {

        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWrongPassword()
            throws Exception {

        String email = "security-" +
            UUID.randomUUID() + "@example.com";

        User user = new User(
            "Security Test User",
            email,
            passwordEncoder.encode("CorrectPassword123!")
        );

        userRepository.saveAndFlush(user);

        String json = """
            {
                "email": "%s",
                "password": "WrongPassword123!"
            }
            """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidJwt()
            throws Exception {

        mockMvc.perform(get("/api/v1/auth/me")
                .header(
                    "Authorization",
                    "Bearer invalid.jwt.token"
                ))
            .andExpect(status().isUnauthorized());
    }
}
