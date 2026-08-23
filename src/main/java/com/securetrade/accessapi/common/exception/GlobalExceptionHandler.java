package com.securetrade.accessapi.common.exception;

import com.securetrade.accessapi.common.enums.ErrorCode;
import com.securetrade.accessapi.dto.response.ApiErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE =
            "https://api.securetrade.com/errors/";
    private static final String INVALID_REQUEST_DETAIL =
            "Request validation failed";
    private static final String DEFAULT_VALIDATION_MESSAGE =
            "Value is not valid";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(
            ResourceNotFoundException exception,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                exception.getMessage(),
                ErrorCode.RESOURCE_NOT_FOUND,
                request,
                null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Object> handleDuplicateResource(
            DuplicateResourceException exception,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.CONFLICT,
                "Duplicate Resource",
                exception.getMessage(),
                ErrorCode.DUPLICATE_RESOURCE,
                request,
                null);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(
            InvalidRequestException exception,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                exception.getMessage(),
                ErrorCode.INVALID_REQUEST,
                request,
                null);
    }

    @ExceptionHandler(SecureTradeAccessDeniedException.class)
    public ResponseEntity<Object> handleSecureTradeAccessDenied(
            SecureTradeAccessDeniedException exception,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                exception.getMessage(),
                ErrorCode.ACCESS_DENIED,
                request,
                null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(
            BadCredentialsException exception,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Invalid username or password",
                ErrorCode.ACCESS_DENIED,
                request,
                null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationFailure(
            AuthenticationException exception,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Authentication failed",
                ErrorCode.ACCESS_DENIED,
                request,
                null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleSpringAccessDenied(
            AccessDeniedException exception,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "Access is denied",
                ErrorCode.ACCESS_DENIED,
                request,
                null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception,
            WebRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            addValidationError(
                    validationErrors,
                    getLastPathPart(violation.getPropertyPath().toString()),
                    violation.getMessage());
        }

        return createInvalidRequestResponse(request, validationErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception,
            WebRequest request) {

        // Keep the real error in the server log
        LOGGER.error("Unexpected error at {}", getInstance(request), exception);

        return createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                ErrorCode.INTERNAL_ERROR,
                request,
                null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            addValidationError(
                    validationErrors,
                    fieldError.getField(),
                    fieldError.getDefaultMessage());
        }

        return createInvalidRequestResponse(request, validationErrors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result
                : exception.getParameterValidationResults()) {

            String parameterName = result.getMethodParameter().getParameterName();
            String fieldName = StringUtils.hasText(parameterName)
                    ? parameterName
                    : "request";

            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                addValidationError(
                        validationErrors,
                        fieldName,
                        error.getDefaultMessage());
            }
        }

        addCrossParameterErrors(
                validationErrors,
                exception.getCrossParameterValidationResults());
        return createInvalidRequestResponse(request, validationErrors);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();
        if (StringUtils.hasText(exception.getPropertyName())) {
            addValidationError(
                    validationErrors,
                    exception.getPropertyName(),
                    DEFAULT_VALIDATION_MESSAGE);
        }

        return createInvalidRequestResponse(request, validationErrors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                "Request body is missing or not valid",
                ErrorCode.INVALID_REQUEST,
                request,
                null);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.createResponseEntity(
                body,
                responseHeaders,
                statusCode,
                request);
    }

    private ResponseEntity<Object> createInvalidRequestResponse(
            WebRequest request,
            Map<String, String> validationErrors) {

        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                INVALID_REQUEST_DETAIL,
                ErrorCode.INVALID_REQUEST,
                request,
                validationErrors);
    }

    private ResponseEntity<Object> createErrorResponse(
            HttpStatus status,
            String title,
            String detail,
            ErrorCode errorCode,
            WebRequest request,
            Map<String, String> validationErrors) {

        ApiErrorResponse response = new ApiErrorResponse(
                getType(errorCode),
                title,
                status.value(),
                detail,
                getInstance(request),
                errorCode.name(),
                Instant.now(),
                validationErrors);

        return createResponseEntity(
                response,
                new HttpHeaders(),
                status,
                request);
    }

    private void addCrossParameterErrors(
            Map<String, String> validationErrors,
            List<MessageSourceResolvable> errors) {

        for (MessageSourceResolvable error : errors) {
            addValidationError(
                    validationErrors,
                    "request",
                    error.getDefaultMessage());
        }
    }

    private void addValidationError(
            Map<String, String> validationErrors,
            String fieldName,
            String message) {

        String safeFieldName = StringUtils.hasText(fieldName)
                ? fieldName
                : "request";
        String safeMessage = StringUtils.hasText(message)
                ? message
                : DEFAULT_VALIDATION_MESSAGE;

        // Keep one clear message for each field
        validationErrors.putIfAbsent(safeFieldName, safeMessage);
    }

    private String getLastPathPart(String path) {
        if (!StringUtils.hasText(path)) {
            return "request";
        }

        int separatorIndex = path.lastIndexOf('.');
        return separatorIndex >= 0
                ? path.substring(separatorIndex + 1)
                : path;
    }

    private String getType(ErrorCode errorCode) {
        return ERROR_TYPE_BASE
                + errorCode.name()
                        .toLowerCase(Locale.ROOT)
                        .replace('_', '-');
    }

    private String getInstance(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }

        String description = request.getDescription(false);
        return description.startsWith("uri=")
                ? description.substring("uri=".length())
                : description;
    }
}
