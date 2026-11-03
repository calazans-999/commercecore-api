
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentConcurrencyIntegrationTest {

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

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();

    // ============================================================
    // DADOS DE TESTE
    // ============================================================

    private Long createCustomer() {

        User user = new User(
            "Concurrent Payment Customer",
            "concurrent-" + UUID.randomUUID() + "@example.com",
            "test-only-hash"
        );

        Long id = userRepository.saveAndFlush(user).getId();
        userIds.add(id);

        return id;
    }

    private Long createProduct(int stock) {

        Category category = categoryRepository.saveAndFlush(
            new Category(
                "Concurrent-Category-" + UUID.randomUUID(),
                "Concurrency integration tests"
            )
        );

        categoryIds.add(category.getId());

        Product product = productRepository.saveAndFlush(
            new Product(
                "Concurrent Test Product",
                "Payment concurrency test",
                new BigDecimal("150.00"),
                stock,
                category
            )
        );

        productIds.add(product.getId());

        return product.getId();
    }

    private int postAsUser(
            Long userId,
            String endpoint) throws Exception {

        return mockMvc.perform(
                post(endpoint)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private Long createOrderWithPayment(
            Long userId,
            Long productId) throws Exception {

        String body = """
            {
              "productId": %d,
              "quantity": 2
            }
            """.formatted(productId);

        int cartStatus = mockMvc.perform(
                post("/api/v1/cart/items")
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse()
            .getStatus();

        assertEquals(200, cartStatus);

        var checkout = mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andReturn()
            .getResponse();

        assertEquals(201, checkout.getStatus());

        Number orderNumber = com.jayway.jsonpath.JsonPath.read(
            checkout.getContentAsString(),
            "$.id"
        );

        Long orderId = orderNumber.longValue();

        assertEquals(
            201,
            postAsUser(
                userId,
                "/api/v1/orders/" + orderId + "/payment"
            )
        );

        return orderId;
    }

    // ============================================================
    // EXECUTAR DUAS REQUISICOES SIMULTANEAMENTE
    // ============================================================

    private List<Integer> race(
            Long userId,
            String endpointA,
            String endpointB) throws Exception {

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {

            Callable<Integer> first = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                        "First request timed out"
                    );
                }

                return postAsUser(userId, endpointA);
            };

            Callable<Integer> second = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                        "Second request timed out"
                    );
                }

                return postAsUser(userId, endpointB);
            };

            Future<Integer> futureA = executor.submit(first);
            Future<Integer> futureB = executor.submit(second);

            assertTrue(
                ready.await(10, TimeUnit.SECONDS),
                "Both requests must be ready"
            );

            start.countDown();

            int statusA =
                futureA.get(30, TimeUnit.SECONDS);

            int statusB =
                futureB.get(30, TimeUnit.SECONDS);

            return List.of(statusA, statusB);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private String paymentStatus(Long orderId) {

        return jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE order_id = ?",
            String.class,
            orderId
        );
    }

    private String orderStatus(Long orderId) {

        return jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?",
            String.class,
            orderId
        );
    }

    private int stockOf(Long productId) {

        return jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ?",
            Integer.class,
            productId
        );
    }

    // ============================================================
    // TESTE 1 - DUAS APROVACOES SIMULTANEAS
    // ============================================================

    @Test
    void shouldApprovePaymentOnlyOnce() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);

        Long orderId =
            createOrderWithPayment(userId, productId);

        String approve =
            "/api/v1/orders/" + orderId + "/payment/approve";

        List<Integer> statuses =
            race(userId, approve, approve);

        assertEquals(
            List.of(200, 409),
            statuses.stream().sorted().toList()
        );

        assertEquals("APPROVED", paymentStatus(orderId));
        assertEquals("PLACED", orderStatus(orderId));
        assertEquals(8, stockOf(productId));
    }

    // ============================================================
    // TESTE 2 - APROVAR E REJEITAR SIMULTANEAMENTE
    // ============================================================

    @Test
    void shouldAllowOnlyOnePaymentDecision()
            throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);

        Long orderId =
            createOrderWithPayment(userId, productId);

        String base =
            "/api/v1/orders/" + orderId + "/payment";

        List<Integer> statuses = race(
            userId,
            base + "/approve",
            base + "/reject"
        );

        assertEquals(
            List.of(200, 409),
            statuses.stream().sorted().toList()
        );

        assertTrue(
            List.of("APPROVED", "REJECTED")
                .contains(paymentStatus(orderId))
        );

        assertEquals("PLACED", orderStatus(orderId));
        assertEquals(8, stockOf(productId));
    }

    // ============================================================
    // TESTE 3 - APROVAR E CANCELAR SIMULTANEAMENTE
    // ============================================================

    @Test
    void shouldKeepOrderAndStockConsistent()
            throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);

        Long orderId =
            createOrderWithPayment(userId, productId);

        String base = "/api/v1/orders/" + orderId;

        List<Integer> statuses = race(
            userId,
            base + "/payment/approve",
            base + "/cancel"
        );

        assertEquals(
            List.of(200, 409),
            statuses.stream().sorted().toList()
        );

        String payment = paymentStatus(orderId);
        String order = orderStatus(orderId);
        int stock = stockOf(productId);

        if ("APPROVED".equals(payment)) {

            // A aprovacao venceu.
            // O cancelamento deve ter sido bloqueado.
            assertEquals("PLACED", order);
            assertEquals(8, stock);

        } else if ("REJECTED".equals(payment)) {

            // O cancelamento venceu.
            // Estoque precisa ser restaurado.
            assertEquals("CANCELLED", order);
            assertEquals(10, stock);

        } else {

            fail(
                "Unexpected payment status: " + payment
            );
        }
    }

    // ============================================================
    // LIMPEZA DOS DADOS DE TESTE
    // ============================================================

    @AfterEach
    void cleanup() {

        for (Long userId : userIds) {

            jdbcTemplate.update("""
                DELETE FROM payments
                WHERE order_id IN (
                    SELECT id FROM orders WHERE user_id = ?
                )
                """, userId);

            jdbcTemplate.update("""
                DELETE FROM order_items
                WHERE order_id IN (
                    SELECT id FROM orders WHERE user_id = ?
                )
                """, userId);

            jdbcTemplate.update("""
                DELETE FROM cart_items
                WHERE cart_id IN (
                    SELECT id FROM carts WHERE user_id = ?
                )
                """, userId);

            jdbcTemplate.update(
                "DELETE FROM orders WHERE user_id = ?",
                userId
            );

            jdbcTemplate.update(
                "DELETE FROM carts WHERE user_id = ?",
                userId
            );
        }

        for (Long productId : productIds) {
            jdbcTemplate.update(
                "DELETE FROM products WHERE id = ?",
                productId
            );
        }

        for (Long categoryId : categoryIds) {
            jdbcTemplate.update(
                "DELETE FROM categories WHERE id = ?",
                categoryId
            );
        }

        for (Long userId : userIds) {
            jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                userId
            );
        }
    }
}
