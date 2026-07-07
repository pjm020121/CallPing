package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 최종 회원가입 요청.
 * - POST /users
 */
@Getter
@NoArgsConstructor
public class SignUpRequestDTO {

    @NotBlank
    private String loginId;

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+])[A-Za-z\\d!@#$%^&*()_+]{8,}$",
        message = "비밀번호는 영문, 숫자, 특수문자를 포함한 8자 이상이어야 합니다."
    )
    private String password;

    @NotBlank
    @Email
    private String email;
}
