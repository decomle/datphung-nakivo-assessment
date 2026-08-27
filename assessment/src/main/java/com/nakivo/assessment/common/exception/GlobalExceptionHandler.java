package com.nakivo.assessment.common.exception;

import com.nakivo.assessment.common.dto.ErrorResponse;
import com.nakivo.assessment.job.exception.JobNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        400,
                        "VALIDATION_ERROR",
                        ex.getMessage(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(HandlerMethodValidationException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        400,
                        "VALIDATION_ERROR",
                        "Invalid request parameter: " + ex.getDetailMessageArguments()[0],
                        Instant.now()
                ));
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(JobNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        404,
                        "JOB_NOT_FOUND",
                        ex.getMessage(),
                        Instant.now()
                ));
    }
}
