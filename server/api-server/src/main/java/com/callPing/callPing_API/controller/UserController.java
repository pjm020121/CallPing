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

    // TODO: (DB/Security 연동 이후) @AuthenticationPrincipal을 사용하여 실제 로그인된 유저 ID로 대체
    private final Long mockLoginUserId = 1L;

    // =================================================================================
    // #1. 회원가입 프로세스
    // =================================================================================

    @PostMapping("/email-verification")
    public ResponseEntity<ApiResponse<Void>> sendSignUpEmail(
            @Valid @RequestBody EmailVerificationRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.sendSignUpEmailVerification(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_SIGNUP_CODE_SENT", "입력하신 이메일로 인증 코드가 발송되었습니다.", request.getRequestURI(), null
        ));
    }

    @PostMapping("/email-verification/check")
    public ResponseEntity<ApiResponse<Void>> verifySignUpEmail(
            @Valid @RequestBody EmailVerificationCheckRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.checkEmailVerification(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_SIGNUP_CODE_VERIFIED", "이메일 인증이 완료되었습니다.", request.getRequestURI(), null
        ));
    }

    @PostMapping("/check-id")
    public ResponseEntity<ApiResponse<Void>> checkLoginId(
            @Valid @RequestBody CheckIdRequestDTO requestDTO, // JSON body로 매핑 (login_id)
            HttpServletRequest request) {

        boolean isAvailable = userService.checkLoginIdAvailable(requestDTO.loginId());

        // 사용 불가한 경우 예외를 던지거나 409 상태코드를 응답하도록 설계할 수 있습니다 (여기서는 명세대로 200 응답 가정)
        if (!isAvailable) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_ID_AVAILABLE", "사용 가능한 아이디입니다.", request.getRequestURI(), null
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SignUpResponseDTO>> signUp(
            @Valid @RequestBody SignUpRequestDTO requestDTO,
            HttpServletRequest request) {

        SignUpResponseDTO responseData = userService.signUp(requestDTO);
        return ResponseEntity.status(201).body(ApiResponse.success(
                201, "USER_SIGNUP_SUCCESS", "회원가입이 성공적으로 완료되었습니다.", request.getRequestURI(), responseData
        ));
    }

    // =================================================================================
    // #2. 아이디 찾기 프로세스
    // =================================================================================

    @PostMapping("/find-id/email-verification")
    public ResponseEntity<ApiResponse<Void>> sendFindIdEmail(
            @Valid @RequestBody EmailVerificationRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.sendFindEmailVerification(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_FINDID_CODE_SENT", "입력하신 이메일로 인증 코드가 발송되었습니다.", request.getRequestURI(), null
        ));
    }

    @PostMapping("/find-id/email-verification/check")
    public ResponseEntity<ApiResponse<Void>> verifyFindIdEmail(
            @Valid @RequestBody EmailVerificationCheckRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.checkEmailVerification(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_FINDID_CODE_VERIFIED", "이메일 인증이 완료되었습니다.", request.getRequestURI(), null
        ));
    }

    @PostMapping("/find-id")
    public ResponseEntity<ApiResponse<FindIdResponseDTO>> findId(
            @Valid @RequestBody FindIdRequestDTO requestDTO,
            HttpServletRequest request) {

        FindIdResponseDTO responseData = userService.findId(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_ID_FOUND", "아이디 찾기에 성공했습니다.", request.getRequestURI(), responseData
        ));
    }

    // =================================================================================
    // #3. 비밀번호 재설정 프로세스
    // =================================================================================

    @PostMapping("/password-reset/email-verification")
    public ResponseEntity<ApiResponse<Void>> sendResetPasswordEmail(
            @Valid @RequestBody EmailVerificationRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.sendPasswordResetEmailVerification(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_RESETPW_CODE_SENT", "입력하신 이메일로 인증 코드가 발송되었습니다.", request.getRequestURI(), null
        ));
    }

    @PostMapping("/password-reset/email-verification/check")
    public ResponseEntity<ApiResponse<Void>> verifyResetPasswordEmail(
            @Valid @RequestBody EmailVerificationCheckRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.checkEmailVerification(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_RESETPW_CODE_VERIFIED", "이메일 인증이 완료되었습니다.", request.getRequestURI(), null
        ));
    }

    @PatchMapping("/password-reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.resetPassword(requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_PASSWORD_RESET_SUCCESS", "비밀번호가 성공적으로 변경되었습니다.", request.getRequestURI(), null
        ));
    }

    // =================================================================================
    // #4. 로그인 된 사용자 계정/권한 관리 (/me)
    // =================================================================================

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> getMyProfile(HttpServletRequest request) {
        UserProfileResponseDTO responseData = userService.getMyProfile(mockLoginUserId);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_PROFILE_FETCHED", "회원 정보 조회가 완료되었습니다.", request.getRequestURI(), responseData
        ));
    }

    @PatchMapping("/me/account/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Valid @RequestBody PasswordChangeRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.updatePassword(mockLoginUserId, requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_PASSWORD_CHANGED", "비밀번호가 안전하게 변경되었습니다.", request.getRequestURI(), null
        ));
    }

    @PatchMapping("/me/account/email")
    public ResponseEntity<ApiResponse<EmailChangeResponseDTO>> updateEmail(
            @Valid @RequestBody EmailChangeRequestDTO requestDTO,
            HttpServletRequest request) {

        // EmailChangeResponseDTO가 반환되지만 명세서에 data: null 이므로 Void 리턴으로 변경 가능(현재는 DTO 유지)
        userService.updateEmail(mockLoginUserId, requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_EMAIL_CHANGED", "이메일 주소가 변경되었습니다.", request.getRequestURI(), null
        ));
    }

    @DeleteMapping("/me/account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(HttpServletRequest request) {
        userService.deleteAccount(mockLoginUserId);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_ACCOUNT_DELETED", "회원 탈퇴가 정상적으로 처리되었습니다.", request.getRequestURI(), null
        ));
    }

    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<PermissionsResponseDTO>> getPermissions(HttpServletRequest request) {
        PermissionsResponseDTO responseData = userService.getPermissions(mockLoginUserId);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_PERMISSIONS_FETCHED", "사용자 권한 설정 조회가 완료되었습니다.", request.getRequestURI(), responseData
        ));
    }

    @PatchMapping("/me/permissions")
    public ResponseEntity<ApiResponse<Void>> updatePermissions(
            @Valid @RequestBody PermissionsUpdateRequestDTO requestDTO,
            HttpServletRequest request) {

        userService.updatePermissions(mockLoginUserId, requestDTO);
        return ResponseEntity.ok(ApiResponse.success(
                200, "USER_PERMISSIONS_UPDATED", "권한 설정이 변경되었습니다.", request.getRequestURI(), null
        ));
    }
}