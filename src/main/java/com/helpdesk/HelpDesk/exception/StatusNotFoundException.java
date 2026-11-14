package com.helpdesk.HelpDesk.exception;

public class StatusNotFoundException extends ResourceNotFoundException {
    public StatusNotFoundException(String message) {
        super(message);
    }
}
