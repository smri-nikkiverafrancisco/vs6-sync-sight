package com.vs6.syncsight.exception;

public class ConnectionFailedException extends IllegalStateException {
    public ConnectionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}