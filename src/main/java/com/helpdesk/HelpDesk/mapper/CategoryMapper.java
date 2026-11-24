package com.helpdesk.HelpDesk.mapper;

import com.helpdesk.HelpDesk.dto.category.CategoryResponse;
import com.helpdesk.HelpDesk.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper implements GenericMapper<Category, CategoryResponse> {
    @Override
    public CategoryResponse toDTO(Category entity) {
        return new CategoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription()
        );
    }
}
