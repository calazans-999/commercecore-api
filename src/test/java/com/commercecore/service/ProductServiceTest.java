package com.commercecore.service;

import com.commercecore.dto.ProductRequest;
import com.commercecore.entity.Category;
import com.commercecore.entity.Product;
import com.commercecore.repository.CategoryRepository;
import com.commercecore.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = new Category(
            "Computers",
            "Computer equipment"
        );

        product = new Product(
            "Gaming Laptop",
            "16GB RAM",
            new BigDecimal("4599.90"),
            15,
            category
        );
    }

    @Test
    void shouldCreateProduct() {
        ProductRequest request = new ProductRequest(
            "Gaming Laptop",
            "16GB RAM",
            new BigDecimal("4599.90"),
            15,
            1L
        );

        when(categoryRepository.findById(1L))
            .thenReturn(Optional.of(category));

        when(productRepository.saveAndFlush(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = productService.create(request);

        assertEquals("Gaming Laptop", response.name());
        assertEquals(new BigDecimal("4599.90"), response.price());
        assertEquals(15, response.stockQuantity());
        assertTrue(response.active());

        verify(productRepository).saveAndFlush(any(Product.class));
    }

    @Test
    void shouldRejectProductWithNonexistentCategory() {
        ProductRequest request = new ProductRequest(
            "Gaming Laptop",
            "16GB RAM",
            new BigDecimal("4599.90"),
            15,
            999L
        );

        when(categoryRepository.findById(999L))
            .thenReturn(Optional.empty());

        ResponseStatusException exception =
            assertThrows(
                ResponseStatusException.class,
                () -> productService.create(request)
            );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldDeactivateProduct() {
        when(productRepository.findById(1L))
            .thenReturn(Optional.of(product));

        productService.delete(1L);

        assertFalse(product.getActive());

        verify(productRepository).findById(1L);
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void shouldReturnNotFoundForMissingProduct() {
        when(productRepository.findById(999L))
            .thenReturn(Optional.empty());

        ResponseStatusException exception =
            assertThrows(
                ResponseStatusException.class,
                () -> productService.findById(999L)
            );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldReturnOnlyActiveProducts() {
        when(productRepository.findByActiveTrue(any(Pageable.class)))
            .thenReturn(Page.empty(PageRequest.of(0, 10)));

        var result = productService.findAll(0, 10);

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());

        verify(productRepository).findByActiveTrue(
            any(Pageable.class)
        );
    }
}