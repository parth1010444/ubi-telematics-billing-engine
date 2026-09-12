package com.example.ubi.exception;

public class AnalyticsLlmUnavailableException extends RuntimeException {

    public AnalyticsLlmUnavailableException(String message) {
        super(message);
    }

    public AnalyticsLlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
