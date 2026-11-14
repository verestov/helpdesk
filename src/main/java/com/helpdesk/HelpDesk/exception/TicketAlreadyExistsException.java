package com.helpdesk.HelpDesk.exception;

public class TicketAlreadyExistsException extends ResourceAlreadyExistsException{
    public TicketAlreadyExistsException(String message) {
        super(message);
    }
}
