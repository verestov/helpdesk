package com.helpdesk.HelpDesk.exception;

public class CategoryAlreadyExistsException extends ResourceAlreadyExistsException {
    public CategoryAlreadyExistsException(String message) {
        super(message);
    }
}
