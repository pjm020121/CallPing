package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일 인증 코드 발송 요청.
 * - POST /users/email-verification
 * - POST /users/find-id/email-verification
 * - POST /users/password-reset/email-verification
 * - POST /users/account-unlock/email-verification
 */
@Getter
@NoArgsConstructor
public class EmailVerificationRequestDTO {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
}
