package com.callPing.callPing_API.client.rag;

import com.callPing.callPing_API.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagClient {

    private final WebClient ragWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String query(String question) {
        return query(question, List.of());
    }

    /**
     * @param history 같은 채팅방의 이전 대화 (오래된 것 → 최근 순). 비어 있으면 단발 질문으로 처리됨
     */
    public String query(String question, List<RagTurn> history) {
        RagQueryRequest request = new RagQueryRequest(question, history == null ? List.of() : history);

        try {
            RagQueryResponse response = ragWebClient.post()
                    .uri("/rag/query")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RagQueryResponse.class)
                    .block();

            if (response == null) {
                throw new ApiException(503, "CHAT_ERR_RAG_EMPTY_RESPONSE", "RAG 서버 응답이 비어있습니다.");
            }

            return response.answer();

        } catch (WebClientResponseException e) {
            String message = extractDetailMessage(e);
            throw new ApiException(e.getStatusCode().value(), "CHAT_ERR_RAG_SERVER", message);
        } catch (Exception e) {
            log.error("RAG 서버 호출 실패", e);
            throw new ApiException(503, "CHAT_ERR_RAG_UNAVAILABLE", "RAG 서버에 연결할 수 없습니다.");
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
            return body;
        } catch (Exception parseEx) {
            return body;
        }
    }

    /** role: "user" 또는 "assistant" */
    public record RagTurn(String role, String content) {}

    record RagQueryRequest(String question, List<RagTurn> history) {}
    record RagQueryResponse(String answer) {}
}