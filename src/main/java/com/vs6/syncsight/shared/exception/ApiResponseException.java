package com.vs6.syncsight.shared.exception;

public class ApiResponseException extends IllegalStateException {
    public ApiResponseException(String message) {
        super(message);
    }

    public ApiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}