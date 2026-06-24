package com.blinkitclone.orderservice.api.exception;

import com.blinkitclone.orderservice.domain.exception.EmptyOrderException;
import com.blinkitclone.orderservice.domain.exception.InvalidOrderStateTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Translates domain and validation exceptions into HTTP responses in one
 * place, instead of every controller method having its own try/catch. This
 * is the layer where "the domain doesn't know about HTTP" becomes concrete:
 * InvalidOrderStateTransitionException carries no status code of its own —
 * this class decides 409 Conflict is the right mapping for it.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidOrderStateTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(InvalidOrderStateTransitionException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmptyOrderException.class)
    public ResponseEntity<ApiError> handleEmptyOrder(EmptyOrderException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), message));
    }

    public record ApiError(Instant timestamp, int status, String message) {
    }
}
