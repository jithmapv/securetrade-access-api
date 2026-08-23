package com.securetrade.accessapi.common.exception;

import com.securetrade.accessapi.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class SecureTradeExceptionTests {

    @Test
    void resourceNotFoundHasExpectedDetails() {
        RuntimeException cause = new RuntimeException("Database error");
        ResourceNotFoundException exception =
                new ResourceNotFoundException("Agent was not found", cause);

        assertInstanceOf(SecureTradeException.class, exception);
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("Agent was not found", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void eachExceptionHasExpectedErrorCode() {
        assertEquals(
                ErrorCode.DUPLICATE_RESOURCE,
                new DuplicateResourceException("Agent already exists").getErrorCode());
        assertEquals(
                ErrorCode.INVALID_REQUEST,
                new InvalidRequestException("Request is not valid").getErrorCode());
        assertEquals(
                ErrorCode.ACCESS_DENIED,
                new SecureTradeAccessDeniedException("Access is not allowed").getErrorCode());
    }
}
