package com.ec7205.event_hub.auth_service_api.adviser;


import com.ec7205.event_hub.auth_service_api.exceptions.BadRequestException;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.exceptions.EntryNotFoundException;
import com.ec7205.event_hub.auth_service_api.exceptions.UnauthorizedException;
import com.ec7205.event_hub.auth_service_api.utils.StandardResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AppWideExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<StandardResponseDto> handleBadRequestException(BadRequestException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(400,ex.getMessage(),ex),
                HttpStatus.BAD_REQUEST
        );
    }
    @ExceptionHandler(DuplicateEntryException.class)
    public ResponseEntity<StandardResponseDto> handleDuplicateEntryException(DuplicateEntryException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(409,ex.getMessage(),ex),
                HttpStatus.CONFLICT
        );
    }
    @ExceptionHandler(EntryNotFoundException.class)
    public ResponseEntity<StandardResponseDto> handleEntryNotFoundException(EntryNotFoundException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(404,ex.getMessage(),ex),
                HttpStatus.NOT_FOUND
        );
    }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<StandardResponseDto> handleEntryNotFoundException(UnauthorizedException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(401,ex.getMessage(),ex),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<StandardResponseDto> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(500, "Database constraint violation", ex),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<StandardResponseDto> handleIllegalStateException(IllegalStateException ex) {
        return new ResponseEntity<StandardResponseDto>(
                new StandardResponseDto(500, ex.getMessage(), ex),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

}
