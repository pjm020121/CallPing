package com.callPing.callPing_API.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private final boolean success;
    private final int status;
    private final String code;      // 예: USER_SIGNUP_SUCCESS, USER_ERR_NOT_FOUND
    private final String message;
    private final String path;
    private final OffsetDateTime timestamp;
    private final T data;

    public static <T> ApiResponse<T> success(int status, String code, String message, String path, T data) {
        return new ApiResponse<>(true, status, code, message, path, OffsetDateTime.now(), data);
    }

    public static <T> ApiResponse<T> fail(int status, String code, String message, String path) {
        return fail(status, code, message, path, null);
    }

    public static <T> ApiResponse<T> fail(int status, String code, String message, String path, T data) {
        return new ApiResponse<>(false, status, code, message, path, OffsetDateTime.now(), data);
    }
}
