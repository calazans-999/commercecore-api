
package com.commercecore.integration;

import com.commercecore.entity.Category;
import com.commercecore.entity.User;
import com.commercecore.repository.CategoryRepository;
import com.commercecore.repository.UserRepository;
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
class CartIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long createCustomer() {
        User user = new User(
            "Cart Test Customer",
            "cart-" + UUID.randomUUID() + "@example.com",
            "test-only-hash"
        );

        return userRepository.saveAndFlush(user).getId();
    }

    private Long createProduct() throws Exception {
        Category category = categoryRepository.saveAndFlush(
            new Category(
                "Cart-Category-" + UUID.randomUUID(),
                "Cart integration tests"
            )
        );

        String json = """
            {
                "name": "Cart Product",
                "description": "Test product",
                "price": 150.00,
                "stockQuantity": 20,
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
            .andReturn();

        Number id = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.id"
        );

        return id.longValue();
    }

    private MvcResult addItem(
            Long userId,
            Long productId,
            int quantity) throws Exception {

        String json = """
            {
                "productId": %d,
                "quantity": %d
            }
            """.formatted(productId, quantity);

        return mockMvc.perform(
                post("/api/v1/cart/items")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        ))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andReturn();
    }

    @Test
    void shouldAddProductToCart() throws Exception {
        Long userId = createCustomer();
        Long productId = createProduct();

        MvcResult result = addItem(userId, productId, 2);

        assertStatus(result, 200);

        String json = result.getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
            2,
            ((Number) JsonPath.read(json, "$.totalItems")).intValue()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
            300.0,
            ((Number) JsonPath.read(json, "$.total")).doubleValue(),
            0.001
        );
    }

    @Test
    void shouldIncreaseQuantityWithoutDuplicate()
            throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct();

        assertStatus(addItem(userId, productId, 2), 200);

        MvcResult result = addItem(userId, productId, 2);
        assertStatus(result, 200);

        String json = result.getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
            4,
            ((Number) JsonPath.read(json, "$.totalItems")).intValue()
        );

        java.util.List<?> items = JsonPath.read(json, "$.items");

        org.junit.jupiter.api.Assertions.assertEquals(1, items.size());
    }

    @Test
    void shouldUpdateCartItemQuantity() throws Exception {
        Long userId = createCustomer();
        Long productId = createProduct();

        MvcResult added = addItem(userId, productId, 2);
        assertStatus(added, 200);

        Number itemId = JsonPath.read(
            added.getResponse().getContentAsString(),
            "$.items[0].id"
        );

        mockMvc.perform(
                put("/api/v1/cart/items/{id}", itemId.longValue())
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        ))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"quantity\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.total").value(450.0));
    }

    @Test
    void shouldRejectInsufficientStock() throws Exception {
        Long userId = createCustomer();
        Long productId = createProduct();

        MvcResult result = addItem(userId, productId, 50);

        assertStatus(result, 409);

        mockMvc.perform(get("/api/v1/cart")
                .with(jwt()
                    .jwt(token -> token.subject(userId.toString()))
                    .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                    )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void shouldRemoveAndClearCart() throws Exception {
        Long userId = createCustomer();
        Long productId = createProduct();

        MvcResult added = addItem(userId, productId, 2);
        assertStatus(added, 200);

        Number itemId = JsonPath.read(
            added.getResponse().getContentAsString(),
            "$.items[0].id"
        );

        mockMvc.perform(
                delete("/api/v1/cart/items/{id}", itemId.longValue())
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));

        assertStatus(addItem(userId, productId, 2), 200);

        mockMvc.perform(delete("/api/v1/cart")
                .with(jwt()
                    .jwt(token -> token.subject(userId.toString()))
                    .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                    )))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                .with(jwt()
                    .jwt(token -> token.subject(userId.toString()))
                    .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                    )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void shouldIsolateCartsBetweenCustomers() throws Exception {
        Long firstUserId = createCustomer();
        Long secondUserId = createCustomer();
        Long productId = createProduct();

        MvcResult added = addItem(firstUserId, productId, 2);
        assertStatus(added, 200);

        Number itemId = JsonPath.read(
            added.getResponse().getContentAsString(),
            "$.items[0].id"
        );

        mockMvc.perform(get("/api/v1/cart")
                .with(jwt()
                    .jwt(token -> token.subject(secondUserId.toString()))
                    .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                    )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(
                delete("/api/v1/cart/items/{id}", itemId.longValue())
                    .with(jwt()
                        .jwt(token -> token.subject(secondUserId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/cart")
                .with(jwt()
                    .jwt(token -> token.subject(firstUserId.toString()))
                    .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER")
                    )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(2));
    }

    private void assertStatus(MvcResult result, int expected)
        throws java.io.UnsupportedEncodingException {

    	org.junit.jupiter.api.Assertions.assertEquals(
        	expected,
        	result.getResponse().getStatus(),
        	result.getResponse().getContentAsString()
    );
}
}