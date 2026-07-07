package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호 찾기 완료 후 새 비밀번호로 변경.
 * - PATCH /users/password-reset
 */
@Getter
@NoArgsConstructor
public class PasswordResetRequestDTO {

    @NotBlank
    private String loginId;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+])[A-Za-z\\d!@#$%^&*()_+]{8,}$",
        message = "비밀번호 형식이 올바르지 않습니다."
    )
    private String password;
}
