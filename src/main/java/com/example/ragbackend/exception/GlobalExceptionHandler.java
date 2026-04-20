package com.example.ragbackend.exception;

import com.example.ragbackend.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException ex) {
        log.warn("Business exception, code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument exception, message={}", ex.getMessage());
        return Result.error(400, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request parameters are invalid");
        log.warn("Method argument validation failed, message={}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request parameters are invalid");
        log.warn("Bind exception, message={}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse("Request parameters are invalid");
        log.warn("Constraint violation, message={}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        String message = ex.getParameterName() + " is required";
        log.warn("Missing request parameter, message={}", message);
        return Result.error(400, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body");
        return Result.error(400, "Request body is invalid or unreadable");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied, message={}", ex.getMessage());
        return Result.error(403, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return Result.error(500, "Internal server error");
    }
}
