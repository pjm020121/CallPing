package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정 이메일 변경 요청.
 * - PATCH /users/me/account/email
 */
@Getter
@NoArgsConstructor
public class EmailChangeRequestDTO {

    @NotBlank
    @Email
    private String email;
}
