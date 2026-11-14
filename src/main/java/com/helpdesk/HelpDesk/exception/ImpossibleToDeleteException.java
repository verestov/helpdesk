package com.helpdesk.HelpDesk.exception;

public class ImposibleToDeleteException extends RuntimeException {
    public ImposibleToDeleteException(String message) {
        super(message);
    }
}
