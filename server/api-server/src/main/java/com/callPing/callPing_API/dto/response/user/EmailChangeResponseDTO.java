package com.callPing.callPing_API.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 이메일 변경 성공 응답.
 * - PATCH /users/me/account/email
 */
@Getter
@AllArgsConstructor
public class EmailChangeResponseDTO {
    private String email;
}
