package com.helpdesk.HelpDesk.exception;

public class EmailAlreadyExistsException extends ResourceAlreadyExistsException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
