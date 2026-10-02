package com.callPing.callPing_API.service.call;

import com.callPing.callPing_API.domain.call.CallTranscript;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiFeedbackServiceImpl implements AiFeedbackService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    //  타임아웃 설정 + ObjectMapper 주입
    public AiFeedbackServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000); // 60초 대기
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    private static final String LORA_API_URL = "http://localhost:18020/lora/feedback";

    @Override
    public String getAiFeedback(List<CallTranscript> transcripts) {
        try {
            log.info("LoRA AI 서버로 피드백 요청 전송 시작... 최대 60초 대기");

            // 1. LoRA 스펙에 맞는 JSON 구조 만들기: {"transcript": [{"speaker":"1", "text":"..."}, ...]}
            List<Map<String, String>> transcriptList = transcripts.stream()
                    .filter(t -> !t.isDeleted()) // 삭제된 텍스트는 제외
                    .map(t -> {
                        Map<String, String> map = new HashMap<>();
                        String speakerNum = t.getSpeaker().replace("화자 ", "");
                        map.put("speaker", speakerNum);
                        map.put("text", t.getText());
                        return map;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("transcript", transcriptList);

            // Map을 JSON String으로 변환
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 2. HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

            // 3. API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    LORA_API_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.info("LoRA AI 서버 분석 완료 응답 수신");
            return response.getBody();

        } catch (Exception e) {
            log.error("LoRA AI 서버 호출 실패", e);
            throw new RuntimeException("AI 분석 요청 중 오류가 발생했습니다.", e);
        }
    }
}