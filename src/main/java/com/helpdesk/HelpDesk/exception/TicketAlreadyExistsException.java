package com.helpdesk.HelpDesk.exception;

public class TicketAlreadyExistsException extends RuntimeException {
  public TicketAlreadyExistsException(String message) {
    super(message);
  }
}
