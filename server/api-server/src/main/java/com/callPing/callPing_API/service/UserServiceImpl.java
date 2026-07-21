package com.callPing.callPing_API.service;

import com.callPing.callPing_API.domain.User;
import com.callPing.callPing_API.domain.UserSetting;
import com.callPing.callPing_API.dto.request.session.LoginRequestDTO;
import com.callPing.callPing_API.dto.request.session.LogoutRequestDTO;
import com.callPing.callPing_API.dto.request.user.*;
import com.callPing.callPing_API.dto.response.session.LoginResponseDTO;
import com.callPing.callPing_API.dto.response.user.*;
import com.callPing.callPing_API.repository.UserRepository;
import com.callPing.callPing_API.repository.UserSettingRepository;
import com.callPing.callPing_API.security.*; ////JWT 프로바이더 추가
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.security.SecureRandom;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final PasswordEncoder passwordEncoder;

    // JWT 토큰 프로바이더 주입
    // private final JwtProvider jwtProvider;

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    //  Redis Key Prefix 및 만료 시간 정의
    private static final String AUTH_CODE_PREFIX = "AUTH_CODE:";
    private static final String FAIL_COUNT_PREFIX = "AUTH_FAIL:";
    private static final String VERIFIED_EMAIL_PREFIX = "VERIFIED_EMAIL:";
    private static final String REFRESH_TOKEN_PREFIX = "RT:";

    private static final long AUTH_CODE_EXPIRATION_MINUTES = 3; // 인증번호 유효시간 3분
    private static final long VERIFIED_EMAIL_EXPIRATION_MINUTES = 30; // 인증완료 상태 유효시간 30분
    private static final int MAX_FAIL_COUNT = 5; // 최대 실패 허용 횟수
    private static final long LOCK_TIME_MINUTES = 30; // 5회 실패 시 30분 잠금

    // =================================================================================
    // #1. 로그인/회원가입 로직
    // =================================================================================

    @Override
    public void sendSignUpEmailVerification(EmailVerificationRequestDTO requestDTO) {
        String email = requestDTO.email();
        if(userRepository.existsByEmail(email)){
            throw new IllegalArgumentException("USER_ERR_DUPLICATED_EMAIL: 이미 가입된 이메일입니다.");
        }
        String verificationCode = generateRandomCode();
        redisTemplate.opsForValue().set(
                "AUTH_CODE:" + email,
                verificationCode,
                Duration.ofMinutes(AUTH_CODE_EXPIRATION_MINUTES)
        );
        sendEmail(email, verificationCode);
        log.info("회원가입 이메일 발송 완료: {}", email);
    }

    @Override
    public void checkEmailVerification(EmailVerificationCheckRequestDTO requestDTO) {
        String email = requestDTO.email();
        String inputCode = requestDTO.verificationCode();
        String storedCode = redisTemplate.opsForValue().get(AUTH_CODE_PREFIX + email);

        if (storedCode == null) {
            throw new IllegalArgumentException("인증 시간이 만료되었습니다. 다시 시도해 주세요.");
        }
        if (!storedCode.equals(inputCode)) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않습니다.");
        }

        // 인증 성공 시 인증 코드 삭제 및 인증 완료 상태 설정
        redisTemplate.delete(AUTH_CODE_PREFIX + email);
        redisTemplate.opsForValue().set(
                VERIFIED_EMAIL_PREFIX + email,
                "VERIFIED",
                Duration.ofMinutes(VERIFIED_EMAIL_EXPIRATION_MINUTES)
        );
        log.info("이메일 인증 성공: {}", email);
    }

    @Override
    public boolean checkLoginIdAvailable(String loginId) {
        // 존재하면 사용 불가(false), 존재하지 않으면 사용 가능(true)
        return !userRepository.existsByLoginId(loginId);
    }

    @Override
    @Transactional
    public SignUpResponseDTO signUp(SignUpRequestDTO requestDTO) {
        if (userRepository.existsByLoginId(requestDTO.loginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        // 유저 생성 및 저장
        String encodedPassword = passwordEncoder.encode(requestDTO.password());

        User user = User.builder()
                .loginId(requestDTO.loginId())
                .password(encodedPassword)
                .email(requestDTO.email())
                .build();

        User savedUser = userRepository.save(user);

        // 유저 기본 설정 생성
        UserSetting defaultSetting = UserSetting.builder()
                .user(savedUser)
                .fileUploadPermission(false)
                .pushPermission(true)
                .build();
        userSettingRepository.save(defaultSetting);

        // 5. 사용된 인증 상태 만료 처리 (재사용 방지)
        redisTemplate.delete(VERIFIED_EMAIL_PREFIX + requestDTO.email());

        return new SignUpResponseDTO(
                savedUser.getId(),
                savedUser.getLoginId(),
                OffsetDateTime.now() // Auditing이 적용되어 있다면 user.getCreatedAt() 사용 권장
        );
    }

    // =================================================================================
    // #2.계정 찾기
    // =================================================================================

    @Override
    public void sendFindEmailVerification(EmailVerificationRequestDTO requestDTO) {
        if (!userRepository.existsByEmail(requestDTO.email())) {
            throw new IllegalArgumentException("가입되지 않은 이메일입니다.");
        }
        sendAndStoreVerificationCode(requestDTO.email());
    }

    @Override
    public FindIdResponseDTO findId(FindIdRequestDTO requestDTO) {
        User user = userRepository.findByEmail(requestDTO.email())
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 가입된 유저를 찾을 수 없습니다."));

        return new FindIdResponseDTO(user.getLoginId());
    }

    @Override
    public void sendPasswordResetEmailVerification(EmailVerificationRequestDTO requestDTO) {
        if (!userRepository.existsByEmail(requestDTO.email())) {
            throw new IllegalArgumentException("가입되지 않은 이메일입니다.");
        }
        sendAndStoreVerificationCode(requestDTO.email());
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetRequestDTO requestDTO) {
        User user = userRepository.findByLoginId(requestDTO.loginId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 요청된 이메일이 해당 아이디의 실제 이메일과 맞는지 교차 검증
        if (!user.getEmail().equals(requestDTO.email())) {
            throw new IllegalArgumentException("회원 정보가 일치하지 않습니다.");
        }

        String encodedPassword = passwordEncoder.encode(requestDTO.password());
        user.changePassword(encodedPassword); // User 엔티티 내에 changePassword 메서드 필요
    }

    // =================================================================================
    // #3. 세션 도메인 (로그인/로그아웃)
    // =================================================================================

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO requestDTO) {
        String loginId = requestDTO.loginId();
        String failKey = FAIL_COUNT_PREFIX + loginId;

        // 1. 계정 잠금 여부 확인 (Redis)
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;

        if (failCount >= MAX_FAIL_COUNT) {
            throw new IllegalStateException("비밀번호 5회 오류로 계정이 잠겼습니다. 잠시 후 다시 시도해주세요.");
        }

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 잘못되었습니다."));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(requestDTO.password(), user.getPassword())) {
            // 실패 카운트 증가 및 만료 시간 설정
            redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, Duration.ofMinutes(LOCK_TIME_MINUTES));

            throw new IllegalArgumentException("아이디 또는 비밀번호가 잘못되었습니다. (실패 횟수: " + (failCount + 1) + "/" + MAX_FAIL_COUNT + ")");
        }

        // 3. 로그인 성공 시 실패 카운트 초기화
        redisTemplate.delete(failKey);

        // 4. 토큰 발급 로직
        // TODO 로직에 맞춰 수정
        // String accessToken = jwtProvider.createAccessToken(user.getId());
        // String refreshToken = jwtProvider.createRefreshToken(user.getId());

        String mockRefreshToken = "mock_refresh_token_xyz";
        Long mockSessionId = user.getId(); // 임시 세션 ID

        // Redis에 Refresh Token 저장 (만료시간 14일 예시)
        // TODO 로직에 맞춰 수정
        // redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + user.getId(), refreshToken, Duration.ofDays(14));

        return new LoginResponseDTO(mockSessionId, user.getId(), mockRefreshToken);
    }

    @Override
    public void logout(LogoutRequestDTO requestDTO) {
        // TODO: SESSION 테이블 또는 Redis에서 전달받은 refreshToken을 찾아 삭제 혹은 만료 처리
        // 실제 환경: Refresh Token이 담긴 요청이 오면 Redis에서 해당 토큰을 삭제
        // JWT의 경우 Access Token을 블랙리스트 처리하기도 함

        // 예시: redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
        log.info("로그아웃 처리 완료 (만료된 토큰: {})", requestDTO.refreshToken());
    }

    // =================================================================================
    // 기존 로직
    // =================================================================================

    @Override
    public UserProfileResponseDTO getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("아이디를 찾을 수 없습니다."));
        return new UserProfileResponseDTO(user.getId(), user.getLoginId(), user.getEmail());
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, PasswordChangeRequestDTO requestDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("아이디를 찾을 수 없습니다."));

        String encodedPassword = passwordEncoder.encode(requestDTO.password());
        user.changePassword(encodedPassword);
    }

    @Override
    @Transactional
    public EmailChangeResponseDTO updateEmail(Long userId, EmailChangeRequestDTO requestDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("아이디를 찾을 수 없습니다."));

        if (userRepository.existsByEmail(requestDTO.email())) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }

        user.changeEmail(requestDTO.email());
        return new EmailChangeResponseDTO(user.getEmail());
    }

    @Override
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        userRepository.delete(user);
    }

    @Override
    public PermissionsResponseDTO getPermissions(Long userId) {
        UserSetting setting = userSettingRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 설정 정보가 존재하지 않습니다."));

        return new PermissionsResponseDTO(
                setting.getFileUploadPermission(),
                setting.getPushPermission()
        );
    }

    @Override
    @Transactional
    public void updatePermissions(Long userId, PermissionsUpdateRequestDTO requestDTO) {
        UserSetting setting = userSettingRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보가 존재하지 않습니다."));

        setting.updatePermissions(requestDTO.fileUploadPermission(), requestDTO.pushNotificationPermission());
    }

    @Override
    @Transactional
    public void updateFcmToken(Long userId, FcmTokenUpdateRequestDTO requestDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보가 존재하지 않습니다."));

        user.updateFcmToken(requestDTO.fcmToken());
    }

    // =================================================================================
    // 내부 헬퍼 메서드
    // =================================================================================

    private void sendAndStoreVerificationCode(String email) {
        String verificationCode = generateRandomCode();
        redisTemplate.opsForValue().set(
                AUTH_CODE_PREFIX + email,
                verificationCode,
                Duration.ofMinutes(AUTH_CODE_EXPIRATION_MINUTES)
        );
        sendEmail(email, verificationCode);
        log.info("이메일 발송 완료: {}", email);
    }

    private String generateRandomCode() {
        SecureRandom secureRandom = new SecureRandom();
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private void sendEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[CallPing] 이메일 인증 번호입니다.");
        message.setText("안녕하세요.\nCallPing 요청하신 인증 번호는 [" + code + "] 입니다.\n3분 안에 입력해 주세요.");
        mailSender.send(message);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }
}