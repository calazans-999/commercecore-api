
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

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
            "Payment Test Customer",
            "payment-" + UUID.randomUUID() + "@example.com",
            "test-only-hash"
        );

        Long id = userRepository.saveAndFlush(user).getId();
        userIds.add(id);

        return id;
    }

    private Long createProduct(int stock) {

        Category category = categoryRepository.saveAndFlush(
            new Category(
                "Payment-Category-" + UUID.randomUUID(),
                "Payment integration tests"
            )
        );

        categoryIds.add(category.getId());

        Product product = productRepository.saveAndFlush(
            new Product(
                "Payment Test Product",
                "Simulated payment integration test",
                new BigDecimal("150.00"),
                stock,
                category
            )
        );

        productIds.add(product.getId());

        return product.getId();
    }

    private void addToCart(
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
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk());
    }

    private Long checkout(Long userId) throws Exception {

        MvcResult result = mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andExpect(status().isCreated())
            .andReturn();

        Number id = com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.id"
        );

        return id.longValue();
    }

    private int paymentRequest(
            Long userId,
            Long orderId,
            String action) throws Exception {

        String endpoint =
            "/api/v1/orders/{orderId}/payment" + action;

        return mockMvc.perform(
                post(endpoint, orderId)
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

    private Long prepareOrder(
            Long userId,
            Long productId) throws Exception {

        addToCart(userId, productId, 2);
        return checkout(userId);
    }

    private int stockOf(Long productId) {

        return jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM products WHERE id = ?",
            Integer.class,
            productId
        );
    }

    // ============================================================
    // TESTE 1 - CRIAR PAGAMENTO PENDENTE
    // ============================================================

    @Test
    void shouldCreatePendingPayment() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);
        Long orderId = prepareOrder(userId, productId);

        mockMvc.perform(
                post("/api/v1/orders/{id}/payment", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.amount").value(300.00));

        assertEquals(8, stockOf(productId));
    }

    // ============================================================
    // TESTE 2 - APROVAR PAGAMENTO
    // ============================================================

    @Test
    void shouldApprovePendingPayment() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);
        Long orderId = prepareOrder(userId, productId);

        assertEquals(201, paymentRequest(userId, orderId, ""));

        mockMvc.perform(
                post("/api/v1/orders/{id}/payment/approve", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.amount").value(300.00));

        assertEquals(8, stockOf(productId));
    }

    // ============================================================
    // TESTE 3 - REJEITAR PAGAMENTO
    // ============================================================

    @Test
    void shouldRejectPendingPayment() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);
        Long orderId = prepareOrder(userId, productId);

        assertEquals(201, paymentRequest(userId, orderId, ""));

        mockMvc.perform(
                post("/api/v1/orders/{id}/payment/reject", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ============================================================
    // TESTE 4 - IMPEDIR PAGAMENTO DUPLICADO
    // ============================================================

    @Test
    void shouldRejectDuplicatePayment() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);
        Long orderId = prepareOrder(userId, productId);

        assertEquals(201, paymentRequest(userId, orderId, ""));
        assertEquals(409, paymentRequest(userId, orderId, ""));

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payments WHERE order_id = ?",
            Integer.class,
            orderId
        );

        assertEquals(1, count);
    }

    // ============================================================
    // TESTE 5 - IMPEDIR ACESSO DE OUTRO CLIENTE
    // ============================================================

    @Test
    void shouldNotExposePaymentToAnotherCustomer()
            throws Exception {

        Long ownerId = createCustomer();
        Long otherUserId = createCustomer();
        Long productId = createProduct(10);

        Long orderId = prepareOrder(ownerId, productId);

        assertEquals(201, paymentRequest(ownerId, orderId, ""));

        mockMvc.perform(
                get("/api/v1/orders/{id}/payment", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(otherUserId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andExpect(status().isNotFound());

        assertEquals(
            404,
            paymentRequest(otherUserId, orderId, "/approve")
        );
    }

    // ============================================================
    // TESTE 6 - CANCELAR PEDIDO COM PAGAMENTO PENDENTE
    // ============================================================

    @Test
    void shouldRejectPendingPaymentWhenOrderIsCancelled()
            throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);
        Long orderId = prepareOrder(userId, productId);

        assertEquals(201, paymentRequest(userId, orderId, ""));
        assertEquals(8, stockOf(productId));

        mockMvc.perform(
                post("/api/v1/orders/{id}/cancel", orderId)
                    .with(jwt()
                        .jwt(token ->
                            token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        String paymentStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM payments WHERE order_id = ?",
            String.class,
            orderId
        );

        assertEquals("REJECTED", paymentStatus);
        assertEquals(10, stockOf(productId));
    }

    // ============================================================
    // TESTE 7 - IMPEDIR CANCELAR PAGAMENTO APROVADO
    // ============================================================

    @Test
    void shouldNotCancelOrderWithApprovedPayment()
            throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(10);
        Long orderId = prepareOrder(userId, productId);

        assertEquals(201, paymentRequest(userId, orderId, ""));
        assertEquals(200, paymentRequest(userId, orderId, "/approve"));

        assertEquals(
            409,
            mockMvc.perform(
                    post("/api/v1/orders/{id}/cancel", orderId)
                        .with(jwt()
                            .jwt(token ->
                                token.subject(userId.toString()))
                            .authorities(
                                new SimpleGrantedAuthority(
                                    "ROLE_CUSTOMER"
                                ))))
                .andReturn()
                .getResponse()
                .getStatus()
        );

        String orderStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?",
            String.class,
            orderId
        );

        assertEquals("PLACED", orderStatus);
        assertEquals(8, stockOf(productId));
    }

    // ============================================================
    // LIMPEZA DOS DADOS CRIADOS PELOS TESTES
    // ============================================================

    @AfterEach
    void cleanup() {

        // Payments deve ser removida antes de orders.
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
