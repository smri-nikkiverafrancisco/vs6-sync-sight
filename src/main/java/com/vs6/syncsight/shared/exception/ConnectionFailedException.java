package com.vs6.syncsight.shared.exception;

public class ConnectionFailedException extends IllegalStateException {
    public ConnectionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}