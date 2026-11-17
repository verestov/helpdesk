package com.helpdesk.HelpDesk.exception;

public class ImpossibleToChangeStatusException extends RuntimeException {
    public ImpossibleToChangeStatusException(String message) {
        super(message);
    }
}
