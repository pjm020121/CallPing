package com.callPing.callPing_API.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * FCM 푸시 토큰 등록/갱신 요청.
 * 앱 실행 시, 또는 토큰 재발급 시(onTokenRefresh) 호출.
 * - PATCH /users/me/fcm-token
 */
public record FcmTokenUpdateRequestDTO(

        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @JsonProperty("fcm_token") // 프론트엔드가 보내는 JSON 키와 이름 맞추기
        String fcmToken
) {}
