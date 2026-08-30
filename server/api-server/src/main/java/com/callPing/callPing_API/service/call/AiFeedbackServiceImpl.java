package com.callPing.callPing_API.service.call;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiFeedbackServiceImpl implements AiFeedbackService {

    private final RestTemplate restTemplate;

    public AiFeedbackServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000); // 60초 (밀리초 단위)
        factory.setReadTimeout(60000);    // 60초 (밀리초 단위)

        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getAiFeedback(List<String> sentences) {
        // AI 팀과 뚫어둔 터널링 포트 (18020) --> 엔드포인트 확인 필수
        String aiServerUrl = "http://localhost:18020/analyze";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // STT 문장 데이터 JSON 바디에 담기
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sentences", sentences);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.info("AI 서버로 분석 요청 전송 시작... 최대 60초 대기");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiServerUrl,
                    requestEntity,
                    String.class
            );

            log.info("AI 서버 분석 완료 응답 수신");

            return response.getBody();

        } catch (Exception e) {
            log.error("AI 서버 통신 중 오류 발생: ", e);
            // 통신 실패 시 폴백 텍스트 반환 (임시)
            return "AI 분석 중 오류가 발생했습니다.\nAI 서버 연결 상태를 확인해 주세요.";
        }
    }
}