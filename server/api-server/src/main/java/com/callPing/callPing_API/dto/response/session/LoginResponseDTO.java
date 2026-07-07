package com.callPing.callPing_API.dto.response.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 성공 응답.
 * - POST /sessions
 */
@Getter
@AllArgsConstructor
public class LoginResponseDTO {
    private Long sessionId;
    private Long userId;
    private String refreshToken;
}
