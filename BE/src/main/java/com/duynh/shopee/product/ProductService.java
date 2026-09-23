package com.duynh.shopee.product;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duynh.shopee.category.Category;
import com.duynh.shopee.category.CategoryService;
import com.duynh.shopee.common.PagedResponse;
import com.duynh.shopee.exception.NotFoundException;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    public ProductService(ProductRepository productRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductSummaryResponse> getProducts(ProductQuery q) {
        q.validate();
        Sort.Direction direction = Sort.Direction.fromString(q.order());
        Sort sort = Sort.by(direction, q.sortBy());
        Pageable pageable = PageRequest.of(q.page() - 1, q.limit(), sort);
        Page<Product> page = productRepository.findAll(ProductSpecifications.withFilter(q), pageable);
        Page<ProductSummaryResponse> pageResponse = page.map(ProductSummaryResponse::from);
        return PagedResponse.from(pageResponse);
    }

    @Transactional
    public ProductResponse getProductById(Long id) {
        productRepository.increaseView(id);
        return ProductResponse.from(findEntityOrThrow(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryService.getEntityById(request.categoryId());

        Product product = new Product(request.name(), request.description(), request.price(),
                request.priceBeforeDiscount(), request.quantity(), category);
        product.setImage(request.image());
        product.replaceImages(request.images());

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findEntityOrThrow(id);
        Category category = categoryService.getEntityById(request.categoryId());

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setPriceBeforeDiscount(request.priceBeforeDiscount());
        product.setQuantity(request.quantity());
        product.setImage(request.image());
        product.setCategory(category);
        product.replaceImages(request.images());

        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findEntityOrThrow(id);
        product.setDeletedAt(Instant.now());
    }

    private Product findEntityOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm id: " + id));
    }
}
