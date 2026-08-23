package com.securetrade.accessapi.common.exception;

import com.securetrade.accessapi.common.enums.ErrorCode;

// Access is not allowed
public class SecureTradeAccessDeniedException extends SecureTradeException {

    public SecureTradeAccessDeniedException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public SecureTradeAccessDeniedException(String message, Throwable cause) {
        super(ErrorCode.ACCESS_DENIED, message, cause);
    }
}
