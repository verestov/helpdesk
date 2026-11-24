package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.category.CategoryResponse;
import com.helpdesk.HelpDesk.dto.category.CreateCategoryRequest;
import com.helpdesk.HelpDesk.dto.category.UpdateCategoryRequest;
import com.helpdesk.HelpDesk.entity.Category;
import com.helpdesk.HelpDesk.exception.BusinessRuleViolationException;
import com.helpdesk.HelpDesk.exception.ResourceAlreadyExistsException;
import com.helpdesk.HelpDesk.exception.ResourceNotFoundException;
import com.helpdesk.HelpDesk.mapper.CategoryMapper;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
        var creatingCategory = request.name().trim().replaceAll("\\s+", " ");
        if(categoryRepository.existsByName(creatingCategory)){
            throw new ResourceAlreadyExistsException("Category already exists");
        }
        var newCategory = new Category();
        newCategory.setName(creatingCategory); 
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
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        return categoryMapper.toDTO(category);
    }

    @Transactional
    public CategoryResponse updateCategoryById(Long id, UpdateCategoryRequest request){
        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        var updatedCategory = request.name().trim().replaceAll("\\s+", " ");
        String categoryName = categoryRepository.getNameById(id);

        // Если false => обновляется имя и нужно проверить есть ли категории с таким именем
        if (!Objects.equals(categoryName, updatedCategory)){
            if(categoryRepository.existsByName(updatedCategory)){
                throw new ResourceAlreadyExistsException("Category already exists");
            }
        }

        category.setName(updatedCategory);
        category.setDescription(request.description());

        var categoryToSave = categoryRepository.save(category);

        return categoryMapper.toDTO(categoryToSave);
    }

    @Transactional
    public void deleteCategoryById(Long id) {
        if(!categoryRepository.existsById(id)){
            throw new ResourceNotFoundException("Category not found");
        }
        if(categoryRepository.hasTickets(id)){
            throw new BusinessRuleViolationException("Category has tickets");
        }
        categoryRepository.deleteById(id);
    }
}
