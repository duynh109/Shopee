package com.duynh.shopee.product;

import com.duynh.shopee.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm id: " + id));
    }

    public Product createProduct(String name, double price, int stock) {
        Product product = new Product(name, price, stock);
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, String name, double price, int stock) {
        Product product = getProductById(id);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new NotFoundException("Không tìm thấy sản phẩm id: " + id);
        }
        productRepository.deleteById(id);
    }
}
