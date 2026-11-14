package com.helpdesk.HelpDesk.exception;

public class PriorityNotFoundException extends ResourceNotFoundException {
    public PriorityNotFoundException(String message) {
        super(message);
    }
}
