package com.computerstore.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.computerstore.payment.exception.PaymentProviderException;
import com.computerstore.shipping.gateway.ShippingProviderException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://computer-store.dev/errors/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Validation failed",
                "One or more fields are invalid.",
                request
        );
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleBadRequest(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "bad-request",
                "Bad request",
                "The request could not be processed.",
                request
        );
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "upload-too-large", "Upload too large",
                "Files must not exceed 5 MiB.", request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ProblemDetail handleInvalidMultipart(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-multipart", "Invalid multipart request",
                "The multipart request is missing a required part or is malformed.", request);
    }

    @ExceptionHandler(FileStorageException.class)
    ProblemDetail handleStorage(FileStorageException exception, HttpServletRequest request) {
        LOGGER.error("File storage error while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "file-storage-error", "File storage error",
                "The file could not be processed.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", exception.getMessage(), request);
    }

    @ExceptionHandler({
            BusinessRuleException.class,
            DuplicateResourceException.class,
            InsufficientStockException.class,
            InvalidStateTransitionException.class
    })
    ProblemDetail handleConflict(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "business-rule-conflict", "Business rule conflict", exception.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedResourceAccessException.class)
    ProblemDetail handleForbidden(UnauthorizedResourceAccessException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "You cannot access this resource.", request);
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    ProblemDetail handleEmailVerificationRequired(
            EmailVerificationRequiredException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "email-verification-required", "Email verification required",
                exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "You cannot access this resource.", request);
    }

    @ExceptionHandler(AuthenticationFailureException.class)
    ProblemDetail handleAuthenticationFailure(AuthenticationFailureException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "authentication-failed", "Authentication failed", exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentProviderException.class)
    ProblemDetail handlePaymentProvider(PaymentProviderException exception, HttpServletRequest request) {
        LOGGER.error("Payment provider error while processing {} {}", request.getMethod(), safeRequestPath(request), exception);
        return problem(HttpStatus.BAD_GATEWAY, "payment-provider-error", "Payment provider error",
                "The payment provider could not complete the request.", request);
    }

    @ExceptionHandler(ShippingProviderException.class)
    ProblemDetail handleShippingProvider(ShippingProviderException exception, HttpServletRequest request) {
        LOGGER.error("Shipping provider error while processing {} {}", request.getMethod(), safeRequestPath(request), exception);
        HttpStatus status = exception.retryAfter() == null ? HttpStatus.BAD_GATEWAY : HttpStatus.SERVICE_UNAVAILABLE;
        ProblemDetail problem = problem(status, "shipping-provider-error", "Shipping provider error",
                "The shipping provider could not complete the request.", request);
        if (exception.retryAfter() != null) problem.setProperty("retryAfterSeconds", exception.retryAfter().toSeconds());
        return problem;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ProblemDetail handleRateLimit(RateLimitExceededException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.TOO_MANY_REQUESTS,
                "rate-limit-exceeded",
                "Too many requests",
                exception.getMessage(),
                request
        );
        problem.setProperty("retryAfterSeconds", 60);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected error while processing {} {}", request.getMethod(), safeRequestPath(request), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal server error",
                "An unexpected error occurred.",
                request
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_TYPE_BASE + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(safeRequestPath(request)));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private String safeRequestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/shipping/webhooks/zipnova/")
                ? "/api/shipping/webhooks/zipnova" : path;
    }
}
