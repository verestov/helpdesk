package com.helpdesk.HelpDesk.exception;

import com.helpdesk.HelpDesk.dto.error.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static  final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleResourceAlreadyExistsException(
            ResourceAlreadyExistsException e
    ) {
        LOGGER.error(e.getMessage());
        var errorDto = new ErrorResponse(
                "ResourceAlreadyExistsException",
                e.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorDto);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException e
    ) {
        LOGGER.error(e.getMessage());

        var errorDto = new ErrorResponse(
                "ResourceNotFoundException",
                e.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorDto);
    }

    @ExceptionHandler(ImpossibleToDeleteException.class)
    public ResponseEntity<ErrorResponse> handleImpossibleToDeleteException(
            ImpossibleToDeleteException e
    ) {
        LOGGER.error("Impossible to delete: category has tickets");

        var errorDto = new ErrorResponse(
                "ImpossibleToDeleteException",
                e.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorDto);
    }
}
