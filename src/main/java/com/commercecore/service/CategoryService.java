package com.commercecore.service;

import com.commercecore.dto.CategoryRequest;
import com.commercecore.dto.CategoryResponse;
import com.commercecore.entity.Category;
import com.commercecore.repository.CategoryRepository;
import com.commercecore.repository.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final ProductRepository productRepository;

    public CategoryService(
            CategoryRepository repository,
            ProductRepository productRepository) {

       this.repository = repository;
       this.productRepository = productRepository;
   }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();

        validateUniqueName(name, null);

        Category category = new Category(
            name,
            request.description()
        );

        return toResponse(repository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return toResponse(getCategory(id));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        String name = request.name().trim();

        if (!category.getName().equalsIgnoreCase(name)) {
            validateUniqueName(name, id);
        }

        category.update(name, request.description());
        return toResponse(repository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);

        if (productRepository.existsByCategoryId(id)) {
           throw new ResponseStatusException(
               HttpStatus.CONFLICT,
               "Cannot delete category because it contains products"
        );
    }

    repository.delete(category);
}

    private Category getCategory(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Category not found: " + id
                ));
    }

    private void validateUniqueName(String name, Long id) {
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Category already exists: " + name
            );
        }
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getDescription()
        );
    }
}
