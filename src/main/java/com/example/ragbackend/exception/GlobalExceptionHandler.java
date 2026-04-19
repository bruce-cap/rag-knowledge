package com.example.ragbackend.exception;

import com.example.ragbackend.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException ex) {
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        return Result.error(400, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return Result.error(400, "Request body is invalid or unreadable");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return Result.error(500, "Internal server error");
    }
}
