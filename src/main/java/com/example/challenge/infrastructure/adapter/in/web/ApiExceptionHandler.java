package com.example.challenge.infrastructure.adapter.in.web;

import com.example.challenge.domain.exception.BusinessRuleViolationException;
import com.example.challenge.domain.exception.InvalidTransactionDataException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Single place where failures become RFC 7807 problem details.
 * <p>
 * Nothing internal leaves this class: unexpected failures are logged with their stack trace and
 * answered with a fixed message, so provider internals and stack traces never reach the client.
 */
@Slf4j
@RestControllerAdvice
class ApiExceptionHandler {

    private static final String VALIDATION_DETAIL = "One or more fields are invalid";
    private static final String UNREADABLE_BODY_DETAIL = "The request body could not be read";
    private static final String UNEXPECTED_DETAIL = "An unexpected error occurred";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .toList();
        return badRequest(VALIDATION_DETAIL, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleInvalidParameter(HandlerMethodValidationException ex) {
        List<String> errors = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .toList();
        return badRequest(VALIDATION_DETAIL, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .toList();
        return badRequest(VALIDATION_DETAIL, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Parameter '" + ex.getName() + "' has an invalid value", List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return badRequest(UNREADABLE_BODY_DETAIL, List.of());
    }

    @ExceptionHandler(InvalidTransactionDataException.class)
    ResponseEntity<ProblemDetail> handleInvalidData(InvalidTransactionDataException ex) {
        return badRequest(ex.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setProperty("code", ex.code().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /**
     * Spring's own HTTP failures (unknown route, unsupported method, unsupported media type) already
     * carry the right status; they must not be turned into a {@code 500}.
     */
    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ProblemDetail> handleErrorResponse(ErrorResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, ex.getBody().getDetail()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedError(Exception ex) {
        log.error("Unexpected error while handling a request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_DETAIL));
    }

    private static ResponseEntity<ProblemDetail> badRequest(String detail, List<String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
