
package com.commercecore.integration;

import com.commercecore.entity.Category;
import com.commercecore.entity.Product;
import com.commercecore.entity.User;

import com.commercecore.repository.CategoryRepository;
import com.commercecore.repository.ProductRepository;
import com.commercecore.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long firstUserId;
    private Long secondUserId;
    private Long productId;
    private Long categoryId;

    private Long createCustomer() {

        User user = new User(
            "Concurrency Test Customer",
            "concurrency-" + UUID.randomUUID() + "@example.com",
            "test-only-hash"
        );

        return userRepository.saveAndFlush(user).getId();
    }

    private Long createProduct() {

        Category category = categoryRepository.saveAndFlush(
            new Category(
                "Concurrency-" + UUID.randomUUID(),
                "Checkout concurrency test"
            )
        );

        categoryId = category.getId();

        Product product = new Product(
            "Last Unit Test Product",
            "Only one unit available",
            new BigDecimal("150.00"),
            1,
            category
        );

        return productRepository.saveAndFlush(product).getId();
    }

    private int addToCart(Long userId) throws Exception {

        String body = """
            {
                "productId": %d,
                "quantity": 1
            }
            """.formatted(productId);

        return mockMvc.perform(
                post("/api/v1/cart/items")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        ))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private int checkout(Long userId) throws Exception {

        return mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    @Test
    void shouldAllowOnlyOneCheckoutForLastProduct()
            throws Exception {

        firstUserId = createCustomer();
        secondUserId = createCustomer();
        productId = createProduct();

        assertEquals(200, addToCart(firstUserId));
        assertEquals(200, addToCart(secondUserId));

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {

            Future<Integer> firstResult = executor.submit(() -> {
                start.await();
                return checkout(firstUserId);
            });

            Future<Integer> secondResult = executor.submit(() -> {
                start.await();
                return checkout(secondUserId);
            });

            // Libera os dois checkouts para competir pelo estoque.
            start.countDown();

            int firstStatus = firstResult.get(
                30, TimeUnit.SECONDS
            );

            int secondStatus = secondResult.get(
                30, TimeUnit.SECONDS
            );

            List<Integer> statuses = List.of(
                firstStatus,
                secondStatus
            );

            assertTrue(
                statuses.contains(201),
                "One checkout must succeed: " + statuses
            );

            assertTrue(
                statuses.contains(409),
                "One checkout must fail: " + statuses
            );

            Integer remainingStock = jdbcTemplate.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?",
                Integer.class,
                productId
            );

            assertEquals(0, remainingStock);

            Integer orderCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM orders
                WHERE user_id IN (?, ?)
                """,
                Integer.class,
                firstUserId,
                secondUserId
            );

            assertEquals(1, orderCount);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @AfterEach
    void cleanup() {

        // Remove apenas os dados criados por este teste.
        deleteUserData(firstUserId);
        deleteUserData(secondUserId);

        if (productId != null) {
            jdbcTemplate.update(
                "DELETE FROM products WHERE id = ?",
                productId
            );
        }

        if (categoryId != null) {
            jdbcTemplate.update(
                "DELETE FROM categories WHERE id = ?",
                categoryId
            );
        }
    }

    private void deleteUserData(Long userId) {

        if (userId == null) {
            return;
        }

        jdbcTemplate.update(
            """
            DELETE FROM order_items
            WHERE order_id IN (
                SELECT id FROM orders WHERE user_id = ?
            )
            """,
            userId
        );

        jdbcTemplate.update(
            """
            DELETE FROM cart_items
            WHERE cart_id IN (
                SELECT id FROM carts WHERE user_id = ?
            )
            """,
            userId
        );

        jdbcTemplate.update(
            "DELETE FROM orders WHERE user_id = ?",
            userId
        );

        jdbcTemplate.update(
            "DELETE FROM carts WHERE user_id = ?",
            userId
        );

        jdbcTemplate.update(
            "DELETE FROM users WHERE id = ?",
            userId
        );
    }
}
