package com.example.moneybug.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

/** 프론트가 응답 본문(res.text())을 그대로 화면에 보여주므로 한글 일반 텍스트로 돌려준다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handle(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(TEXT).body(e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<String> handle(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(TEXT).body(e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<String> handleInvalidInput(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(TEXT).body("입력값을 확인해 주세요.");
    }
}
