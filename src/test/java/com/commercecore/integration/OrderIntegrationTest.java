
package com.commercecore.integration;

import com.commercecore.entity.Category;
import com.commercecore.entity.Product;
import com.commercecore.entity.User;
import com.commercecore.repository.CategoryRepository;
import com.commercecore.repository.ProductRepository;
import com.commercecore.repository.UserRepository;

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
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long createCustomer() {

        User user = new User(
            "Order Test Customer",
            "order-" + UUID.randomUUID() + "@example.com",
            "test-only-hash"
        );

        return userRepository.saveAndFlush(user).getId();
    }

    private Long createProduct(int stock) {

        Category category = categoryRepository.saveAndFlush(
            new Category(
                "Order-Category-" + UUID.randomUUID(),
                "Order integration tests"
            )
        );

        Product product = new Product(
            "Mouse Gamer Test",
            "Product for checkout testing",
            new BigDecimal("150.00"),
            stock,
            category
        );

        return productRepository.saveAndFlush(product).getId();
    }

    private MvcResult addItem(
            Long userId,
            Long productId,
            int quantity) throws Exception {

        String body = """
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
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    void shouldCheckoutSuccessfully() throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(20);

        addItem(userId, productId, 2);

        MvcResult result = mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PLACED"))
            .andExpect(jsonPath("$.totalAmount").value(300.00))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andReturn();

        assertNotNull(result.getResponse().getContentAsString());

        Product product = productRepository.findById(productId)
            .orElseThrow();

        assertEquals(18, product.getStockQuantity());

        mockMvc.perform(
                get("/api/v1/cart")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void shouldRejectCheckoutWithEmptyCart() throws Exception {

        Long userId = createCustomer();

        mockMvc.perform(
                get("/api/v1/cart")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isOk());

        mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectCheckoutWhenStockIsInsufficient()
            throws Exception {

        Long userId = createCustomer();
        Long productId = createProduct(5);

        addItem(userId, productId, 5);

        // Simula uma redução do estoque após o item
        // ter sido adicionado ao carrinho.
        Product product = productRepository.findById(productId)
            .orElseThrow();

        product.setStockQuantity(2);
        productRepository.saveAndFlush(product);

        mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isConflict());

        assertEquals(
            2,
            productRepository.findById(productId)
                .orElseThrow()
                .getStockQuantity()
        );

        mockMvc.perform(
                get("/api/v1/cart")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(5));
    }

    @Test
    void shouldNotExposeOrdersToOtherCustomers() throws Exception {

        Long firstUser = createCustomer();
        Long secondUser = createCustomer();
        Long productId = createProduct(20);

        addItem(firstUser, productId, 2);

        MvcResult checkout = mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token -> token.subject(firstUser.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isCreated())
            .andReturn();

        Number orderId = com.jayway.jsonpath.JsonPath.read(
            checkout.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(
                get("/api/v1/orders/{id}", orderId.longValue())
                    .with(jwt()
                        .jwt(token -> token.subject(secondUser.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isNotFound());

        mockMvc.perform(
                get("/api/v1/orders")
                    .with(jwt()
                        .jwt(token -> token.subject(secondUser.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }


    @Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void shouldRollbackCheckoutWhenAnotherProductHasInsufficientStock()
        	throws Exception {

        Long userId = createCustomer();
        Long firstProductId = createProduct(10);
        Long secondProductId = createProduct(3);

        addItem(userId, firstProductId, 2);
        addItem(userId, secondProductId, 3);

        // O estoque muda depois que os itens entram no carrinho.
        Product secondProduct = productRepository.findById(secondProductId)
            .orElseThrow();
        secondProduct.setStockQuantity(1);
        productRepository.saveAndFlush(secondProduct);

        mockMvc.perform(
                post("/api/v1/orders/checkout")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isConflict());

        // Nenhum desconto de estoque deve permanecer.
        assertEquals(
            10,
            productRepository.findById(firstProductId)
                .orElseThrow()
                .getStockQuantity()
        );

        assertEquals(
            1,
            productRepository.findById(secondProductId)
                .orElseThrow()
                .getStockQuantity()
        );

        // O checkout rejeitado nao deve criar um pedido.
        mockMvc.perform(
                get("/api/v1/orders")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));

        // O carrinho permanece com as cinco unidades solicitadas.
        mockMvc.perform(
                get("/api/v1/cart")
                    .with(jwt()
                        .jwt(token -> token.subject(userId.toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(5));
    }
}
