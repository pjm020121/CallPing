package com.callPing.callPing_API.client.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagClient {

    private final WebClient ragWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String query(String question) {
        RagQueryRequest request = new RagQueryRequest(question);

        try {
            RagQueryResponse response = ragWebClient.post()
                    .uri("/rag/query")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RagQueryResponse.class)
                    .block();

            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "RAG 서버 응답이 비어있습니다.");
            }

            return response.answer();

        } catch (WebClientResponseException e) {
            String message = extractDetailMessage(e);
            throw new ResponseStatusException(
                    HttpStatus.valueOf(e.getStatusCode().value()),
                    message
            );
        } catch (Exception e) {
            log.error("RAG 서버 호출 실패", e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RAG 서버에 연결할 수 없습니다."
            );
        }
    }

    private String extractDetailMessage(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.isBlank()) {
            return "RAG 서버 오류";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("detail")) {
                return node.get("detail").asText();
            }
            return body; // detail 필드 없으면 fallback
        } catch (Exception parseEx) {
            return body; // JSON 파싱 실패 시 원문 그대로
        }
    }

    record RagQueryRequest(String question) {}
    record RagQueryResponse(String answer) {}
}