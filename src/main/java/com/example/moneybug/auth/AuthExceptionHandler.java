package com.example.moneybug.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

/** 프론트가 응답 본문(res.text())을 그대로 화면에 보여주므로 한글 일반 텍스트로 돌려준다. */
@RestControllerAdvice
public class AuthExceptionHandler {

    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<String> handle(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(TEXT).body(e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<String> handle(EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(TEXT).body(e.getMessage());
    }
}
