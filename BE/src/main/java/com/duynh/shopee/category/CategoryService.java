package com.duynh.shopee.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duynh.shopee.exception.ConflictException;
import com.duynh.shopee.exception.FieldValidationException;
import com.duynh.shopee.exception.NotFoundException;
import com.duynh.shopee.product.ProductRepository;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** GET /api/categories */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /** POST /api/admin/categories */
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByName(name)) {
            throw new FieldValidationException("name", "Tên danh mục đã tồn tại");
        }

        Category category = categoryRepository.save(new Category(name));
        return CategoryResponse.from(category);
    }

    /** PUT /api/admin/categories/{id} */
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getEntityById(id);
        String name = request.name().trim();

        if (categoryRepository.existsByNameAndIdNot(name, id)) {
            throw new FieldValidationException("name", "Tên danh mục đã tồn tại");
        }
        category.setName(name);
        categoryRepository.saveAndFlush(category);
        return CategoryResponse.from(category);
    }

    /** DELETE /api/admin/categories/{id} */
    @Transactional
    public void delete(Long id) {
        Category category = getEntityById(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Danh mục vẫn còn sản phẩm, không xoá được");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public Category getEntityById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục id: " + id));
    }
}
