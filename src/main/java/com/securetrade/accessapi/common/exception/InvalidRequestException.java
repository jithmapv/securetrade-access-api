package com.securetrade.accessapi.common.exception;

import com.securetrade.accessapi.common.enums.ErrorCode;

// Request data is not valid
public class InvalidRequestException extends SecureTradeException {

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(ErrorCode.INVALID_REQUEST, message, cause);
    }
}
