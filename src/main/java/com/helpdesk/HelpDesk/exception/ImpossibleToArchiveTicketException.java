package com.helpdesk.HelpDesk.exception;

public class ImpossibleToArchiveTicketException extends RuntimeException {
    public ImpossibleToArchiveTicketException(String message) {
        super(message);
    }
}
