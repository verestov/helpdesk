package com.helpdesk.HelpDesk.mapper;

import com.helpdesk.HelpDesk.dto.user.UserResponse;
import com.helpdesk.HelpDesk.entity.User;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class UserMapper implements GenericMapper<User, UserResponse> {
    @Override
    public UserResponse toDTO(@NonNull User entity) {
        return new UserResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getRole()
        );
    }
}
