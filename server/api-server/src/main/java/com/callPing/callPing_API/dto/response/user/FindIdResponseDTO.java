package com.callPing.callPing_API.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 아이디 찾기 성공 응답.
 * - POST /users/find-id
 */
@Getter
@AllArgsConstructor
public class FindIdResponseDTO {
    private String loginId;
}
