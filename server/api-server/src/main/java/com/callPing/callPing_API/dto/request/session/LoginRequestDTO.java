package com.callPing.callPing_API.dto.request.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 요청.
 * - POST /sessions
 */
@Getter
@NoArgsConstructor
public class LoginRequestDTO {

    @NotBlank
    private String loginId;

    @NotBlank
    private String password;
}
