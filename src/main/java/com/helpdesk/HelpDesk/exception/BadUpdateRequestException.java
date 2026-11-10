package com.helpdesk.HelpDesk.exception;

public class BadUpdateRequestException extends RuntimeException {
    public BadUpdateRequestException(String message) {
        super(message);
    }
}
