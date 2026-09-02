package com.callPing.callPing_API.service.guide;

import com.callPing.callPing_API.domain.guide.GuideMessage;
import com.callPing.callPing_API.domain.guide.GuideSession;
import com.callPing.callPing_API.dto.request.guide.*;
import com.callPing.callPing_API.dto.response.guide.*;
import com.callPing.callPing_API.repository.guide.GuideMessageRepository;
import com.callPing.callPing_API.repository.guide.GuideSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GuideServiceImpl implements GuideService {

    private final GuideSessionRepository guideSessionRepository;
    private final GuideMessageRepository guideMessageRepository;
    private final RestTemplate restTemplate;

    public GuideServiceImpl(GuideSessionRepository guideSessionRepository,
                            GuideMessageRepository guideMessageRepository) {
        this.guideSessionRepository = guideSessionRepository;
        this.guideMessageRepository = guideMessageRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000); // 60초 대기
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    @Transactional
    public GuideResponseDTO createGuide(Long userId, GuideRequestDTO request) {
        String title = request.description().length() > 15
                ? request.description().substring(0, 15) + "..."
                : request.description();

        GuideSession session = GuideSession.builder()
                .userId(userId)
                .category(request.category())
                .title(title)
                .build();
        guideSessionRepository.save(session);

        // 💡 더미 데이터 대신 실제 AI 서버에 분석 요청!
        String aiAnswer = requestLoRaAiModel(request.description());

        GuideMessage message = GuideMessage.builder()
                .guideSession(session)
                .question(request.description())
                .answer(aiAnswer)
                .build();
        guideMessageRepository.save(message);

        return new GuideResponseDTO(
                session.getId(), message.getId(), session.getTitle(),
                message.getQuestion(), message.getAnswer(), session.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public GuideMessageResponseDTO addMessage(Long userId, Long guideId, GuideMessageRequestDTO request) {
        GuideSession session = guideSessionRepository.findByIdAndUserId(guideId, userId)
                .orElseThrow(() -> new IllegalArgumentException("접근 권한이 없거나 존재하지 않는 가이드입니다."));

        String aiAnswer = requestLoRaAiModel(request.question());

        GuideMessage message = GuideMessage.builder()
                .guideSession(session)
                .question(request.question())
                .answer(aiAnswer)
                .build();
        guideMessageRepository.save(message);

        return new GuideMessageResponseDTO(
                session.getId(), message.getId(), message.getQuestion(),
                message.getAnswer(), message.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideListResponseDTO> getGuides(Long userId) {
        return guideSessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> new GuideListResponseDTO(s.getId(), s.getTitle(), s.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideMessageHistoryResponseDTO> getMessages(Long userId, Long guideId) {
        GuideSession session = guideSessionRepository.findByIdAndUserId(guideId, userId)
                .orElseThrow(() -> new IllegalArgumentException("접근 권한이 없거나 존재하지 않는 가이드입니다."));

        return guideMessageRepository.findAllByGuideSessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(m -> new GuideMessageHistoryResponseDTO(m.getId(), m.getQuestion(), m.getAnswer(), m.getCreatedAt()))
                .toList();
    }

    // ==============================================================================
    // AI 서버 통신 메서드
    // ==============================================================================
    private String requestLoRaAiModel(String questionText) {
        // TODO; 실제 엔드포인트 주소 확인 필수
        String aiServerUrl = "http://127.0.0.1:18020/lora/generate";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // TODO: JSON 키값("question", "text" 등)에 맞게 수정 (검토)
            Map<String, Object> body = new HashMap<>();
            body.put("question", questionText);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("LoRA AI 서버에 가이드 텍스트 생성 요청 중... 최대 60초 대기");
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiServerUrl,
                    requestEntity,
                    String.class
            );
            log.info("LoRA AI 서버 응답 완료");

            return response.getBody();

        } catch (Exception e) {
            log.error("LoRA AI 서버 통신 오류: ", e);
            // 통신 실패 시 서버가 터지지 않게 안전한 기본 안내 문구 반환
            return "현재 AI 모델이 혼잡하여 답변을 생성할 수 없습니다. 잠시 후 다시 시도해주세요.";
        }
    }
}