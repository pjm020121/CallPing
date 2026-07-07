package com.callPing.callPing_API.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 파일 업로드 / 푸시 알림 권한 조회 응답.
 * - GET /users/me/permissions
 */
@Getter
@AllArgsConstructor
public class PermissionsResponseDTO {
    private boolean fileUploadPermission;
    private boolean pushNotificationPermission;
}
