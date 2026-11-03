
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderCancellationIntegrationTest {

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
    // METODOS AUXILIARES
    // ============================================================

    private Long createCustomer() {

        User user = new User(
            "Cancellation Test Customer",
            "cancel-" + UUID.randomUUID() + "@example.com",
            "test-only-hash"
        );

        Long id = userRepository.saveAndFlush(user).getId();
        userIds.add(id);

        return id;
    }

    private Long createProduct(int stock) {

        Category category = categoryRepository.saveAndFlush(
            new Category(
                "Cancel-Category-" + UUID.randomUUID(),
                "Category for cancellation tests"
            )
        );

        categoryIds.add(category.getId());

        Product product = productRepository.saveAndFlush(
            new Product(
                "Cancellation Test Product",
                "Product for cancellation integration tests",
                new BigDecimal("150.00"),
                stock,
                category
            )
        );

        productIds.add(product.getId());

        return product.getId();
    }

    private void addItem(
            Long userId,
            Long productId,
            int quantity) throws Exception {

        String body = """
            {
                "productId": %d,
                "quantity": %d
            }
            """.formatted(productId, quantity);

        mockMvc.perform(
                post("/api/v1/cart/items")
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString())
                        )
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        ))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk());
    }

    private Long checkout(Long userId) throws Exception {

        MvcResult result = mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString())
                        )
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        )))
            .andExpect(status().isCreated())
            .andReturn();

        Number id = com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.id"
        );

        return id.longValue();
    }

    private int cancel(Long userId, Long orderId)
            throws Exception {

        return mockMvc.perform(
                post("/api/v1/orders/{id}/cancel", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString())
                        )
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        )))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private int getStock(Long productId) {

        Integer stock = jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ?",
            Integer.class,
            productId
        );

        return stock;
    }

    // ============================================================
    // TESTE 1 - CANCELAMENTO COM SUCESSO
    // ============================================================

    @Test
    void shouldCancelOrderAndRestoreStock() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);

        addItem(userId, productId, 2);

        Long orderId = checkout(userId);

        // Checkout desconta duas unidades.
        assertEquals(8, getStock(productId));

        mockMvc.perform(
                post("/api/v1/orders/{id}/cancel", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString())
                        )
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.totalAmount").value(300.00))
            .andExpect(jsonPath("$.items[0].quantity").value(2));

        // O estoque deve voltar ao valor original.
        assertEquals(10, getStock(productId));
    }

    // ============================================================
    // TESTE 2 - IMPEDIR CANCELAMENTO DUPLICADO
    // ============================================================

    @Test
    void shouldRejectDuplicateCancellation() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);

        addItem(userId, productId, 2);

        Long orderId = checkout(userId);

        assertEquals(8, getStock(productId));

        // Primeira tentativa: sucesso.
        assertEquals(200, cancel(userId, orderId));

        assertEquals(10, getStock(productId));

        // Segunda tentativa: conflito.
        assertEquals(409, cancel(userId, orderId));

        // O estoque nao pode aumentar novamente.
        assertEquals(10, getStock(productId));
    }

    // ============================================================
    // TESTE 3 - PEDIDO DE OUTRO CLIENTE
    // ============================================================

    @Test
    void shouldNotCancelAnotherCustomersOrder()
            throws Exception {

        Long ownerId = createCustomer();
        Long otherUserId = createCustomer();

        Long productId = createProduct(10);

        addItem(ownerId, productId, 2);

        Long orderId = checkout(ownerId);

        assertEquals(8, getStock(productId));

        // Outro cliente nao pode cancelar o pedido.
        assertEquals(404, cancel(otherUserId, orderId));

        // O estoque continua descontado.
        assertEquals(8, getStock(productId));

        // O pedido continua PLACED.
        mockMvc.perform(
                get("/api/v1/orders/{id}", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(ownerId.toString())
                        )
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PLACED"));
    }

    // ============================================================
    // TESTE 4 - CANCELAMENTOS SIMULTANEOS
    // ============================================================

    @Test
    void shouldRestoreStockOnlyOnceWithConcurrentCancellations()
            throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);

        addItem(userId, productId, 2);

        Long orderId = checkout(userId);

        assertEquals(8, getStock(productId));

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {

            Future<Integer> firstResult = executor.submit(() -> {
                start.await();
                return cancel(userId, orderId);
            });

            Future<Integer> secondResult = executor.submit(() -> {
                start.await();
                return cancel(userId, orderId);
            });

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

            // Apenas uma solicitacao deve ser aprovada.
            assertTrue(
                statuses.contains(200),
                "One cancellation must succeed: " + statuses
            );

            assertTrue(
                statuses.contains(409),
                "One cancellation must fail: " + statuses
            );

            // As duas unidades sao devolvidas apenas uma vez.
            assertEquals(10, getStock(productId));

            String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                orderId
            );

            assertEquals("CANCELLED", orderStatus);

        } finally {

            start.countDown();
            executor.shutdownNow();

            // Evita limpar os dados com threads ainda em execucao.
            if (!executor.awaitTermination(
                    30, TimeUnit.SECONDS)) {
                fail("Cancellation threads did not terminate");
            }
        }
    }

    // ============================================================
    // LIMPEZA AUTOMATICA DOS DADOS DOS TESTES
    // ============================================================

    @AfterEach
    void cleanup() {

        // Remove somente registros criados nesta classe.
        // Nunca utiliza TRUNCATE ou apaga o banco inteiro.

        for (Long userId : userIds) {

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
