package com.securetrade.accessapi.common.exception;

import com.securetrade.accessapi.common.enums.ErrorCode;

import java.util.Objects;

// Base error for this application
public abstract class SecureTradeException extends RuntimeException {

    private final ErrorCode errorCode;

    protected SecureTradeException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "Error code cannot be null");
    }

    protected SecureTradeException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "Error code cannot be null");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
