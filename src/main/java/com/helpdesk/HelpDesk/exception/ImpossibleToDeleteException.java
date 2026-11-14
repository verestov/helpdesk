package com.helpdesk.HelpDesk.exception;

public class ImpossibleToDeleteException extends RuntimeException {
    public ImpossibleToDeleteException(String message) {
        super(message);
    }
}
