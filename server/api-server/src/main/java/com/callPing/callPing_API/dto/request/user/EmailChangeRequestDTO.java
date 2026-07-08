package com.callPing.callPing_API.dto.request.user;

import com.callPing.callPing_API.dto.common.constant.ValidationRule;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 계정 이메일 변경 요청.
 * - PATCH /users/me/account/email
 */
public record EmailChangeRequestDTO(

        @NotBlank(message = ValidationRule.EMAIL_BLANK_MESSAGE)
        @Email(message = ValidationRule.EMAIL_FORMAT_MESSAGE)
        String email
) {}
