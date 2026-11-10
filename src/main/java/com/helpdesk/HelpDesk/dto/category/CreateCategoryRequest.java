package com.helpdesk.HelpDesk.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Only ADMIN can create category
public record CreateCategoryRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description
) {
}
