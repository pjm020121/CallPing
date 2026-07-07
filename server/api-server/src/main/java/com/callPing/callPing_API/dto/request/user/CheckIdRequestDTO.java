package com.callPing.callPing_API.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이디 중복 확인 요청.
 * - POST /users/check-id
 */
@Getter
@NoArgsConstructor
public class CheckIdRequestDTO {

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
    private String loginId;
}
