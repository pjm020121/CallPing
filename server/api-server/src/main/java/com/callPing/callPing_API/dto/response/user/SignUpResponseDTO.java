package com.callPing.callPing_API.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 회원가입 성공 응답.
 * - POST /users
 */
@Getter
@AllArgsConstructor
public class SignUpResponseDTO {
    private Long userId;
    private String loginId;
    private OffsetDateTime createdAt;
}
