package com.helpdesk.HelpDesk.dto.comment;

import com.helpdesk.HelpDesk.dto.user.UserResponse;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long ticketId,
        UserResponse author,
        String content,
        LocalDateTime createdAt
) {
}
