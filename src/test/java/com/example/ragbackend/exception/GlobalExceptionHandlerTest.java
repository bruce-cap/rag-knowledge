package com.example.ragbackend.exception;

import com.example.ragbackend.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessExceptionUsesBusinessCode() {
        Result<?> result = handler.handleBusinessException(new BusinessException(403, "Forbidden"));

        assertEquals(403, result.getCode());
        assertEquals("Forbidden", result.getMessage());
    }

    @Test
    void handleIllegalArgumentExceptionReturnsBadRequest() {
        Result<?> result = handler.handleIllegalArgumentException(new IllegalArgumentException("Bad input"));

        assertEquals(400, result.getCode());
        assertEquals("Bad input", result.getMessage());
    }

    @Test
    void handleUnreadableMessageReturnsBadRequest() {
        Result<?> result = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("invalid"));

        assertEquals(400, result.getCode());
        assertEquals("Request body is invalid or unreadable", result.getMessage());
    }
}
