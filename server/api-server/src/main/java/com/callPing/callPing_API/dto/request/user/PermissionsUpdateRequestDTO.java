package com.callPing.callPing_API.dto.request.user;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파일 업로드 / 푸시 알림 권한 토글 요청.
 * - PATCH /users/me/permissions
 */
@Getter
@NoArgsConstructor
public class PermissionsUpdateRequestDTO {

    private Boolean fileUploadPermission;
    private Boolean pushNotificationPermission;
}
