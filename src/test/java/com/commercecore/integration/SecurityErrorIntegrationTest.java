
package com.commercecore.integration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ============================================================
    // TESTE 1 - REQUISICAO SEM AUTENTICACAO
    // ============================================================

    @Test
    void shouldReturnStructuredUnauthorizedError()
            throws Exception {

        mockMvc.perform(
                get("/api/v1/cart")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message")
                .value("Authentication required"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/cart"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    // ============================================================
    // TESTE 2 - CLIENTE SEM PERMISSAO ADMIN
    // ============================================================

    @Test
    void shouldReturnStructuredForbiddenError()
            throws Exception {

        mockMvc.perform(
                post("/api/v1/products")
                    .with(jwt()
                        .jwt(token -> token.subject("1"))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        ))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.message")
                .value("Access denied"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/products"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
