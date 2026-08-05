package com.callPing.callPing_API.config;

import com.callPing.callPing_API.dto.common.ApiResponse;
import com.callPing.callPing_API.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 우리가 직접 정의한 예외 (code 포함)
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException e, HttpServletRequest request) {
        return ResponseEntity.status(e.getStatus()).body(
                ApiResponse.fail(e.getStatus(), e.getCode(), e.getMessage(), request.getRequestURI())
        );
    }

    // 아직 ApiException으로 안 바꾼 기존 ResponseStatusException 호환용
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
            ResponseStatusException e, HttpServletRequest request) {
        log.error("[Global Exception] Internal Server Error: ", e);
        int status = e.getStatusCode().value();
        return ResponseEntity.status(status).body(
                ApiResponse.fail(status, "ERROR", e.getReason(), request.getRequestURI())
        );
    }

    // @Valid 검증 실패 (DTO의 @NotBlank, @NotNull 등)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("잘못된 요청입니다.");

        return ResponseEntity.status(400).body(
                ApiResponse.fail(400, "VALIDATION_ERROR", message, request.getRequestURI())
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        return ResponseEntity.status(400).body(
                ApiResponse.fail(400, "INVALID_PARAMETER", "잘못된 요청 파라미터입니다.", request.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception e, HttpServletRequest request) {

        return ResponseEntity.status(500).body(
                ApiResponse.fail(500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.", request.getRequestURI())
        );
    }
}