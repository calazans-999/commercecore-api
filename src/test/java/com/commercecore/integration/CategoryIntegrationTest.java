
package com.commercecore.integration;

import com.commercecore.entity.Category;
import com.commercecore.repository.CategoryRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
class CategoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository repository;

    @Test
    void shouldCreateCategoryViaHttp() throws Exception {
        String name = "Test-" + UUID.randomUUID();

        String json = """
            {
                "name": "%s",
                "description": "Integration test"
            }
            """.formatted(name);

        mockMvc.perform(post("/api/v1/categories")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.description")
                .value("Integration test"));
    }

    @Test
    void shouldRejectInvalidCategoryViaHttp() throws Exception {
        String json = """
            {
                "name": "",
                "description": "Invalid"
            }
            """;

        mockMvc.perform(post("/api/v1/categories")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void shouldRetrieveCategoryViaHttp() throws Exception {
        Category category = repository.saveAndFlush(
            new Category(
                "Integration-" + UUID.randomUUID(),
                "Created for testing"
            )
        );

        mockMvc.perform(get(
                "/api/v1/categories/{id}",
                category.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description")
                .value("Created for testing"));
    }
}
