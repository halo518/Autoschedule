package com.example.moneybug.auth;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("Email already registered");
    }
}
