package com.callPing.callPing_API.service;

import com.callPing.callPing_API.dto.request.user.*;
import com.callPing.callPing_API.dto.response.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    // private final UserRepository userRepository;
    // TODO 추후 Repository 주입 필요
    // [GET] 내 프로필 정보 조회
    public UserProfileResponseDTO getMyProfile(Long userId) {
        // TODO: userRepository.findById(userId)로 조회 후 매핑
        return new UserProfileResponseDTO(userId, "abcdef1234", "user1@example.com");
    }

    //[PATCH] 로그인 상태에서 비밀번호 변경
    @Transactional
    public void updatePassword(Long userId, PasswordChangeRequestDTO requestDTO) {
        // TODO: 유저 조회 -> 암호화(BCrypt) -> 비밀번호 변경 로직 수행
    }

    //[PATCH] 계정 이메일 주소 변경
    @Transactional
    public EmailChangeResponseDTO updateEmail(Long userId, EmailChangeRequestDTO requestDTO) {
        // TODO: 유저 조회 -> 이메일 변경 처리
        return new EmailChangeResponseDTO(requestDTO.email());
    }

    //[DELETE] 회원 탈퇴 처리
    @Transactional
    public void deleteAccount(Long userId) {
        // TODO: 유저 삭제 로직 수행 (또는 Soft Delete 상태 변경)
    }

    // [GET] 유저 권한 조회 (파일 업로드, 푸시 알림)

    public PermissionsResponseDTO getPermissions(Long userId) {
        // TODO: 유저 권한 테이블 혹은 컬럼 조회
        return new PermissionsResponseDTO(false, false);
    }

    //[PATCH] 권한 설정 토글 업데이트

    @Transactional
    public void updatePermissions(Long userId, PermissionsUpdateRequestDTO requestDTO) {
        // TODO: 권한 정보 수정 반영 (requestDTO.fileUploadPermission(), requestDTO.pushNotificationPermission())
    }

    // [PATCH] FCM 토큰 등록 및 갱신
    @Transactional
    public void updateFcmToken(Long userId, FcmTokenUpdateRequestDTO requestDTO) {
        // TODO: FCM 토큰 테이블 유효성 검증 후 저장/수정 (requestDTO.fcmToken())
    }
}