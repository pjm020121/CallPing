package com.callPing.callPing_API.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 비밀번호 암호화 엔코더
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )

                // JWT 기반 인증 (세션 생성 X)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        //0. h2 콘솔 허용 --> 추후 제외해도 되는 구문
                        .requestMatchers("/h2-console/**").permitAll()

                        //1. 회원가입, 이메일 인증, 아이디 중복확인, 찾기, 비밀번호 재설정, 계정 잠금 해제는 누구나 접근 가능
                        .requestMatchers(
                                "/api/v1/users",
                                "/api/v1/users/email-verification/**",
                                "/api/v1/users/check-id",
                                "/api/v1/users/find-id/**",
                                "/api/v1/users/password-reset/**",
                                "/api/v1/users/unlock/**"
                        ).permitAll()
                        //2. /me로 시작하는 마이페이지 및 회원 정보 수정/탈퇴 등의 API는 반드시 로그인이 필요 (@AuthenticationPrincipal 작동 지점)
                        .requestMatchers("/api/v1/users/me/**").authenticated()
                        //3. 그 외 요청 우선 허용
                        .anyRequest().permitAll()
                )
                // UsernamePasswordAuthenticationFilter 실행 전에 JwtAuthenticationFilter를 먼저 동작
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}