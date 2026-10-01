package vn.com.fis.consentcore.registry.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import vn.com.fis.consentcore.shared.error.BusinessException;
import vn.com.fis.consentcore.shared.error.ErrorCode;

@RestControllerAdvice
class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex.errorCode());
        return response(status, ex.errorCode().name(), ex.getMessage(), ex.details(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                "Request validation failed", details, request);
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                ex.getMessage(), Map.of(), request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                "Request syntax, header or parameter is invalid", Map.of(), request);
    }


    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.VALIDATION_ERROR.name(),
                "Evidence upload exceeds the configured size limit", Map.of(), request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, ErrorCode.CONFLICT.name(),
                "The request conflicts with the current persisted state", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error correlationId={} path={}",
                request.getHeader("X-Correlation-Id"), request.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                "Unexpected server error", Map.of(), request);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return ResponseEntity.status(status).body(new ApiError(
                clock.instant(), status.value(), code, message, correlationId, details));
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case CONSENT_NOT_FOUND, NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONSENT_VALIDITY_INVALID, VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case CONSENT_DATA_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case CONSENT_DATA_REQUIREMENT_UNRESOLVED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CONSENT_ALREADY_EXISTS,
                 CONSENT_INVALID_STATE_TRANSITION,
                 CONSENT_EVIDENCE_NOT_VERIFIED,
                 CONSENT_CONTENT_NOT_FINALIZED,
                 CONSENT_DECISION_REQUIRED,
                 CONSENT_NOT_YET_EXPIRED,
                 CONSENT_AUTHORIZATION_BINDING_INVALID,
                 CONSENT_PREPARATION_SELECTION_INVALID,
                 CONSENT_PREPARATION_STALE,
                 IDEMPOTENCY_KEY_REUSED,
                 CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
