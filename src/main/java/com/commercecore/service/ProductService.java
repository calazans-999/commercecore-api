package com.commercecore.service;

import com.commercecore.dto.ProductRequest;
import com.commercecore.dto.ProductResponse;
import com.commercecore.entity.Category;
import com.commercecore.entity.Product;
import com.commercecore.repository.CategoryRepository;
import com.commercecore.repository.ProductRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = getCategory(request.categoryId());

        Product product = new Product(
            request.name().trim(),
            request.description(),
            request.price(),
            request.stockQuantity(),
            category
        );

        product = productRepository.saveAndFlush(product);

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(int page, int size) {
       return productRepository.findByActiveTrue(
          PageRequest.of(
            page,
            size,
            Sort.by("id").ascending()
           )
       ).map(this::toResponse);
   }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return toResponse(getProduct(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        Category category = getCategory(request.categoryId());

        product.update(
            request.name().trim(),
            request.description(),
            request.price(),
            request.stockQuantity(),
            category
        );

        return toResponse(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = getProduct(id);
        product.deactivate();
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Product not found: " + id
            ));
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Category not found: " + id
            ));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getActive(),
            product.getCategory().getId(),
            product.getCategory().getName(),
            product.getCreatedAt()
        );
    }
}