package com.callPing.callPing_API.dto.request.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그아웃 요청 (refresh token 만료 처리).
 * - DELETE /sessions
 */
@Getter
@NoArgsConstructor
public class LogoutRequestDTO {

    @NotBlank
    private String refreshToken;
}
