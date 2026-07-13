package com.callPing.callPing_API.controller;

import com.callPing.callPing_API.dto.common.ApiResponse;
import com.callPing.callPing_API.dto.request.user.*;
import com.callPing.callPing_API.dto.response.user.*;
import com.callPing.callPing_API.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 로그인된 사용자의 식별자(ID)를 가져오는 임시 하드코딩
    //TODO (DB연결 이후 @AuthenticationPrincipal 을 사용)
    private final Long mockLoginUserId = 1L;

    // 내 프로필 정보 조회

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> getMyProfile(HttpServletRequest request) {
        UserProfileResponseDTO responseData = userService.getMyProfile(mockLoginUserId);

        return ResponseEntity.ok(ApiResponse.success(
                200,
                "USER_PROFILE_FETCHED",
                "회원 정보 조회가 완료되었습니다.",
                request.getRequestURI(),
                responseData
        ));
    }

    // 로그인 상태에서 새로운 비밀번호로 변경
    @PatchMapping("/me/account/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Valid @RequestBody PasswordChangeRequestDTO passwordRequestDTO,
            HttpServletRequest request) {

        userService.updatePassword(mockLoginUserId, passwordRequestDTO);

        return ResponseEntity.ok(ApiResponse.success(
                200,
                "USER_PASSWORD_CHANGED",
                "비밀번호가 안전하게 변경되었습니다.",
                request.getRequestURI(),
                null
        ));
    }

    //인증 단계를 거쳐 사용자의 계정 이메일 주소를 변경
    @PatchMapping("/me/account/email")
    public ResponseEntity<ApiResponse<EmailChangeResponseDTO>> updateEmail(
            @Valid @RequestBody EmailChangeRequestDTO emailRequestDTO,
            HttpServletRequest request) {

        EmailChangeResponseDTO responseData = userService.updateEmail(mockLoginUserId, emailRequestDTO);

        return ResponseEntity.ok(ApiResponse.success(
                200,
                "USER_EMAIL_CHANGED",
                "이메일 주소가 변경되었습니다.",
                request.getRequestURI(),
                responseData
        ));
    }

    //회원 탈퇴 요청
    @DeleteMapping("/me/account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(HttpServletRequest request) {

        userService.deleteAccount(mockLoginUserId);

        return ResponseEntity.ok(ApiResponse.success(
                200,
                "USER_ACCOUNT_DELETED",
                "회원 탈퇴가 정상적으로 처리되었습니다.",
                request.getRequestURI(),
                null
        ));
    }

    // 권한 설정 화면 진입 시 사용자의 파일 업로드 및 푸시 알림 권한 가/부 조회

    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<PermissionsResponseDTO>> getPermissions(HttpServletRequest request) {

        PermissionsResponseDTO responseData = userService.getPermissions(mockLoginUserId);

        return ResponseEntity.ok(ApiResponse.success(
                200,
                "USER_PERMISSIONS_FETCHED",
                "사용자 권한 설정 조회가 완료되었습니다.",
                request.getRequestURI(),
                responseData
        ));
    }

    //권한 설정 화면에서 파일 업로드나 푸시 알림 스위치를 토글할 때 값을 업데이트
    @PatchMapping("/me/permissions")
    public ResponseEntity<ApiResponse<Void>> updatePermissions(
            @Valid @RequestBody PermissionsUpdateRequestDTO permissionsUpdateRequestDTO,
            HttpServletRequest request) {

        userService.updatePermissions(mockLoginUserId, permissionsUpdateRequestDTO);

        return ResponseEntity.ok(ApiResponse.success(
                200,
                "USER_PERMISSIONS_UPDATED",
                "권한 설정이 변경되었습니다.",
                request.getRequestURI(),
                null
        ));
    }

    //로그인 상태에서 클라이언트가 발급받은 FCM 토큰을 서버에 등록하거나 갱신
    @PatchMapping("/me/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @Valid @RequestBody FcmTokenUpdateRequestDTO fcmTokenUpdateRequestDTO,
            HttpServletRequest request) {

        userService.updateFcmToken(mockLoginUserId, fcmTokenUpdateRequestDTO);

        return ResponseEntity.ok(ApiResponse.success(
                200,
                "USER_FCM_TOKEN_UPDATED",
                "FCM 토큰이 등록되었습니다.",
                request.getRequestURI(),
                null
        ));
    }
}