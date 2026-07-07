package com.callPing.callPing_API.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 내 정보(프로필) 조회 응답.
 * - GET /users/me
 */
@Getter
@AllArgsConstructor
public class UserProfileResponseDTO {
    private Long userId;
    private String loginId;
    private String email;
}
