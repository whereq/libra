package com.whereq.libra.exception;

/**
 * Exception thrown when job queue is full or quota is exceeded
 */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }

    public QuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
