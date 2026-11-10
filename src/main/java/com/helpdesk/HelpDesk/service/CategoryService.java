package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.category.CategoryResponse;
import com.helpdesk.HelpDesk.dto.category.CreateCategoryRequest;
import com.helpdesk.HelpDesk.entity.Category;
import com.helpdesk.HelpDesk.exception.CategoryAlreadyExistsException;
import com.helpdesk.HelpDesk.exception.CategoryNotFoundException;
import com.helpdesk.HelpDesk.mapper.CategoryMapper;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Autowired
    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryMapper categoryMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public CategoryResponse createCategory(@NonNull CreateCategoryRequest request){
        if(categoryRepository.existsByName(request.name())){
            throw new CategoryAlreadyExistsException("Category already exists");
        }
        var newCategory = new Category();
        newCategory.setName(request.name());
        newCategory.setDescription(request.description());
        newCategory.setCreatedAt(LocalDateTime.now());

        var savedCategory = categoryRepository.save(newCategory);

        return categoryMapper.toDTO(savedCategory);
    }

    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();

        return categories.stream()
                .map(categoryMapper::toDTO)
                .toList();
    }

    public CategoryResponse getCategoryById(Long id) {
        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));

        return categoryMapper.toDTO(category);
    }
}
