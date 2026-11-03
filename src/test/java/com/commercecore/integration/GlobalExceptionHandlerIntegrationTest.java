
package com.commercecore.integration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ============================================================
    // TESTE 1 - JSON MALFORMADO
    // ============================================================

    @Test
    void shouldReturnStructuredErrorForMalformedJson()
            throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ invalid json }")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message")
                .value("Invalid or malformed JSON request body"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/auth/login"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    // ============================================================
    // TESTE 2 - VALIDACAO DE CAMPOS
    // ============================================================

    @Test
    void shouldReturnFieldErrorsForInvalidRegistration()
            throws Exception {

        String body = """
            {
              "name": "",
              "email": "invalid-email",
              "password": "123"
            }
            """;

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message")
                .value("Validation failed"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.fields.name").exists())
            .andExpect(jsonPath("$.fields.email").exists())
            .andExpect(jsonPath("$.fields.password").exists());
    }

    // ============================================================
    // TESTE 3 - PARAMETRO COM TIPO INCORRETO
    // ============================================================

    @Test
    void shouldReturnStructuredErrorForInvalidPathVariable()
            throws Exception {

        mockMvc.perform(
                get("/api/v1/products/abc")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message")
                .value("Invalid value for parameter: id"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/products/abc"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    // ============================================================
    // TESTE 4 - RECURSO INEXISTENTE
    // ============================================================

    @Test
    void shouldReturnStructuredErrorForMissingProduct()
            throws Exception {

        mockMvc.perform(
                get("/api/v1/products/{id}", Long.MAX_VALUE)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message")
                .value("Product not found: " + Long.MAX_VALUE))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/products/" + Long.MAX_VALUE))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    // ============================================================
    // TESTE 5 - CREDENCIAIS INVALIDAS
    // ============================================================

    @Test
    void shouldReturnStructuredErrorForInvalidCredentials()
            throws Exception {

        String body = """
            {
              "email": "nonexistent-test@example.com",
              "password": "InvalidPassword123!"
            }
            """;

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message")
                .value("Invalid email or password"))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/auth/login"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
