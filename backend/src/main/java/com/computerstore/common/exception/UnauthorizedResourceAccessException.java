package com.computerstore.common.exception;

public class UnauthorizedResourceAccessException extends RuntimeException {

    public UnauthorizedResourceAccessException(String message) {
        super(message);
    }
}
