
package com.commercecore.integration;

import com.commercecore.entity.Category;
import com.commercecore.repository.CategoryRepository;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category createCategory() {
        return categoryRepository.saveAndFlush(
            new Category(
                "Category-" + UUID.randomUUID(),
                "Created for product testing"
            )
        );
    }

    @Test
    void shouldCreateProductViaHttp() throws Exception {
        Category category = createCategory();

        String json = """
            {
                "name": "Gaming Laptop",
                "description": "32GB RAM",
                "price": 5999.90,
                "stockQuantity": 10,
                "categoryId": %d
            }
            """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/products")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name")
                .value("Gaming Laptop"))
            .andExpect(jsonPath("$.stockQuantity")
                .value(10));
    }

    @Test
    void shouldRejectNegativePrice() throws Exception {
        Category category = createCategory();

        String json = """
            {
                "name": "Invalid Laptop",
                "price": -100,
                "stockQuantity": 5,
                "categoryId": %d
            }
            """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/products")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectNonexistentCategory() throws Exception {
        String json = """
            {
                "name": "Gaming Mouse",
                "price": 199.90,
                "stockQuantity": 20,
                "categoryId": 999999999
            }
            """;

        mockMvc.perform(post("/api/v1/products")
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldSoftDeleteProduct() throws Exception {
        Category category = createCategory();

        String json = """
            {
                "name": "Product To Delete",
                "description": "Soft delete integration test",
                "price": 150.00,
                "stockQuantity": 5,
                "categoryId": %d
            }
            """.formatted(category.getId());

        MvcResult result = mockMvc.perform(
                post("/api/v1/products")
                    .with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                    ))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

        Number id = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(delete(
                "/api/v1/products/{id}",
                id.longValue())
                .with(jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
                )))
            .andExpect(status().isNoContent());
    }
}
