package com.duynh.shopee.product;

import com.duynh.shopee.category.Category;
import com.duynh.shopee.category.CategoryService;
import com.duynh.shopee.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    public ProductService(ProductRepository productRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm id: " + id));
    }

    public Product createProduct(CreateProductRequest request) {
        Category category = categoryService.getEntityById(request.categoryId());
        Product product = new Product(request.name(), request.price(), request.stock(), category);
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, CreateProductRequest request) {
        Product product = getProductById(id);
        Category category = categoryService.getEntityById(request.categoryId());
        product.setName(request.name());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(category);
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new NotFoundException("Không tìm thấy sản phẩm id: " + id);
        }
        productRepository.deleteById(id);
    }
}
