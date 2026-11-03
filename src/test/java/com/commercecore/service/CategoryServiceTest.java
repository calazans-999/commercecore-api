package com.commercecore.service;

import com.commercecore.dto.CategoryRequest;
import com.commercecore.entity.Category;
import com.commercecore.repository.CategoryRepository;
import com.commercecore.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category(
            "Electronics",
            "Electronic devices"
        );
    }

    @Test
    void shouldCreateCategory() {
        CategoryRequest request = new CategoryRequest(
            "Electronics",
            "Electronic devices"
        );

        when(categoryRepository.save(any(Category.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = categoryService.create(request);

        assertEquals("Electronics", response.name());
        assertEquals("Electronic devices", response.description());

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void shouldRejectDuplicateCategory() {
        CategoryRequest request = new CategoryRequest(
            "Electronics",
            "Duplicate"
        );

        when(categoryRepository.existsByNameIgnoreCase("Electronics"))
            .thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> categoryService.create(request)
        );

        assertEquals(
            HttpStatus.CONFLICT,
            exception.getStatusCode()
        );

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldListCategories() {
        when(categoryRepository.findAll())
            .thenReturn(List.of(category));

        var response = categoryService.findAll();

        assertEquals(1, response.size());
        assertEquals("Electronics", response.get(0).name());
    }

    @Test
    void shouldUpdateCategory() {
        when(categoryRepository.findById(1L))
            .thenReturn(Optional.of(category));

        when(categoryRepository.save(any(Category.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryRequest request = new CategoryRequest(
            "Computers",
            "Computers and accessories"
        );

        var response = categoryService.update(1L, request);

        assertEquals("Computers", response.name());
        assertEquals("Computers and accessories",
                     response.description());

        verify(categoryRepository).save(category);
    }

    @Test
    void shouldRejectMissingCategory() {
        when(categoryRepository.findById(999L))
            .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> categoryService.findById(999L)
        );

        assertEquals(
            HttpStatus.NOT_FOUND,
            exception.getStatusCode()
        );
    }

    @Test
    void shouldRejectDeletingCategoryWithProducts() {
        when(categoryRepository.findById(1L))
            .thenReturn(Optional.of(category));

        when(productRepository.existsByCategoryId(1L))
            .thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> categoryService.delete(1L)
        );

        assertEquals(
            HttpStatus.CONFLICT,
            exception.getStatusCode()
        );

        verify(categoryRepository, never()).delete(any(Category.class));
    }
}