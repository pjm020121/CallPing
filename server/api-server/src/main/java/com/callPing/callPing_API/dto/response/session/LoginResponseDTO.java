package com.callPing.callPing_API.dto.response.session;

/**
 * 로그인 성공 응답.
 * - POST /sessions
 */
public record LoginResponseDTO(
        Long sessionId,
        Long userId,
        String refreshToken
) {}
