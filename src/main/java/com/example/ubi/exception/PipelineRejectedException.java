package com.example.ubi.exception;

public class PipelineRejectedException extends RuntimeException {

    public PipelineRejectedException(String message) {
        super(message);
    }
}
