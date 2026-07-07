package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 계정 잠금 해제 요청 (인증 완료 후).
 * - PATCH /users/account-unlock
 */
public record AccountUnlockRequestDTO(
    @NotBlank
    String loginId,

    @NotBlank
    @Email
    String email
) {}