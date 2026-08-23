package com.securetrade.accessapi.common.exception;

import com.securetrade.accessapi.common.enums.ErrorCode;

// Resource was not found
public class ResourceNotFoundException extends SecureTradeException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, cause);
    }
}
