package com.coretrust.bank.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import com.coretrust.bank.observability.RequestIdFilter;
import org.slf4j.MDC;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.stream.Collectors;

/** NOTES:
 * Every error leaves the API as an RFC 9457 ProblemDetail with two extra fields:
 * errorCode (stable, machine-readable) and timestamp. Validation errors add "errors".
 */
@Slf4j //Causes lombok to generate a logger field.
@RestControllerAdvice
//ResponseEntityExceptionHandler - Spring's built-in handler for framework errors like bad JSON, validation failures, and 404s
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ---------- Our own exceptions ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("Business error [{}] on {}: {}", ex.getErrorCode(), req.getRequestURI(), ex.getMessage());
        return respond(ex.getErrorCode(), ex.getMessage(), req.getRequestURI());
    }

    // ---------- Persistence ----------

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        log.warn("Optimistic lock conflict on {}", req.getRequestURI());
        return respond(ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified by another request. Please retry.", req.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        // Never return the raw SQL message: it leaks table/constraint names
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DUPLICATE_RESOURCE,
                "The request conflicts with existing data.", req.getRequestURI());
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSort(PropertyReferenceException ex, HttpServletRequest req) {
        return respond(ErrorCode.VALIDATION_FAILED,
                "Invalid sort property: '%s'.".formatted(ex.getPropertyName()), req.getRequestURI());
    }

    // ---------- Validation outside controllers (@Validated services) ----------

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        ResponseEntity<ProblemDetail> response =
                respond(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", req.getRequestURI());
        response.getBody().setProperty("errors", errors);
        return response;
    }

    // ---------- Security ----------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return respond(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action.", req.getRequestURI());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return respond(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.", req.getRequestURI());
    }

    /** Spring Security throws this for users whose isEnabled() is false: INACTIVE, i.e. setup not finished. */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ProblemDetail> handleSetupPending(DisabledException ex, HttpServletRequest req) {
        return respond(ErrorCode.ACCOUNT_SETUP_PENDING,
                "Your profile isn't active yet. Finish setting it up using the link we emailed you, "
                        + "or ask CoreTrust Support to send a new one.", req.getRequestURI());
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ProblemDetail> handleLocked(LockedException ex, HttpServletRequest req) {
        return respond(ErrorCode.ACCOUNT_DISABLED,
                "Your profile is locked. Please contact CoreTrust Support.", req.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return respond(ErrorCode.UNAUTHORIZED,
                "Authentication is required to access this resource.", req.getRequestURI());
    }

    // ---------- Catch-all ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {}", req.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", req.getRequestURI());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(ex.getErrorCode(), ex.getMessage(), req.getRequestURI());
        pd.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(pd.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(pd);
    }

    // ---------- Overrides of Spring MVC's built-in handling ----------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String message = fe.getDefaultMessage();
            if (fe.isBindingFailure()) {
                // Conversion failed (e.g. "BANANA" into an enum): replace Spring's technical text
                Class<?> requiredType = fe.contains(TypeMismatchException.class)
                        ? fe.unwrap(TypeMismatchException.class).getRequiredType()
                        : null;
                message = typeMismatchMessage(fe.getField(), fe.getRejectedValue(), requiredType);
            }
            errors.putIfAbsent(fe.getField(), message);
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));

        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", path(request));
        pd.setProperty("errors", errors);
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err -> errors.putIfAbsent(name, err.getDefaultMessage()));
        });

        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, "One or more parameters are invalid.", path(request));
        pd.setProperty("errors", errors);
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail pd = problem(ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or not valid JSON.", path(request));
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail pd = problem(ErrorCode.RESOURCE_NOT_FOUND,
                "No endpoint found for this path.", path(request));
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String name = (ex instanceof MethodArgumentTypeMismatchException m) ? m.getName() : ex.getPropertyName();
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED,
                typeMismatchMessage(name, ex.getValue(), ex.getRequiredType()), path(request));
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String detail = (ex instanceof MissingRequestHeaderException m)
                ? "Required header '%s' is missing.".formatted(m.getHeaderName())
                : "The request is missing required information.";
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, detail, path(request));
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    // ---------- Helpers ----------

    //wraps a ProblemDetail in a ResponseEntity with the correct HTTP status.
    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, String path) {
        ProblemDetail pd = problem(code, detail, path);
        return ResponseEntity.status(pd.getStatus()).body(pd);
    }

    //builds the ProblemDetail itself - Every handler uses it, which is why all errors share the same shape.
    private ProblemDetail problem(ErrorCode code, String detail, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
        pd.setTitle(code.getTitle());
        pd.setInstance(URI.create(path));
        pd.setProperty("errorCode", code.name());
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return pd;
    }

    //path gets the request URI from WebRequest
    private String path(WebRequest request) {
        return (request instanceof ServletWebRequest swr) ? swr.getRequest().getRequestURI() : "";
    }

    /** Human-readable conversion errors; lists the allowed values for enums. */
    private static String typeMismatchMessage(String name, Object value, Class<?> requiredType) {
        if (requiredType != null && requiredType.isEnum()) {
            String allowed = Arrays.stream(requiredType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "Invalid value '%s' for '%s'. Allowed values: %s.".formatted(value, name, allowed);
        }
        if (requiredType != null && Temporal.class.isAssignableFrom(requiredType)) {
            return "Invalid date '%s' for '%s'. Use the format yyyy-MM-dd.".formatted(value, name);
        }
        if (requiredType != null && Number.class.isAssignableFrom(requiredType)) {
            return "Invalid value '%s' for '%s': must be a number.".formatted(value, name);
        }
        return "Invalid value '%s' for '%s'.".formatted(value, name);
    }
}
