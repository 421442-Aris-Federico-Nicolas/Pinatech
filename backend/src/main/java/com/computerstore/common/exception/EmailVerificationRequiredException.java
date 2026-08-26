package com.computerstore.common.exception;

public class EmailVerificationRequiredException extends RuntimeException {

    public EmailVerificationRequiredException() {
        super("Email verification is required to continue checkout.");
    }
}
