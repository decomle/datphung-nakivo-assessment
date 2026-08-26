package com.nakivo.assessment.common.exception;

import com.nakivo.assessment.job.exception.JobNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Void> handleJobNotFound() {
        return ResponseEntity.notFound().build();
    }
}
