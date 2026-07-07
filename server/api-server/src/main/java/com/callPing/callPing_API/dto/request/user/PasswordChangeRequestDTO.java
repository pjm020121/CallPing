package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 상태에서 비밀번호 변경.
 * - PATCH /users/me/account/password
 */
@Getter
@NoArgsConstructor
public class PasswordChangeRequestDTO {

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+])[A-Za-z\\d!@#$%^&*()_+]{8,}$",
        message = "비밀번호 형식이 올바르지 않습니다."
    )
    private String password;
}
