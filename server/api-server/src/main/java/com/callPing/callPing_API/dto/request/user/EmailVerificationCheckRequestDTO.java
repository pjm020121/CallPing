package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일 인증 코드(6자리) 검증 요청.
 * - POST /users/email-verification/check
 * - POST /users/find-id/email-verification/check
 * - POST /users/password-reset/email-verification/check
 * - POST /users/account-unlock/email-verification/check
 */
@Getter
@NoArgsConstructor
public class EmailVerificationCheckRequestDTO {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드는 6자리 숫자입니다.")
    private String verificationCode;
}
