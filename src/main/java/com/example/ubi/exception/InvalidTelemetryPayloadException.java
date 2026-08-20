package com.example.ubi.exception;

public class InvalidTelemetryPayloadException extends RuntimeException {

    public InvalidTelemetryPayloadException(String message) {
        super(message);
    }
}
