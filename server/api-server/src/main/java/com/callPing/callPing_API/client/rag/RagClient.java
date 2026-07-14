package com.callPing.callPing_API.client.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
@RequiredArgsConstructor
public class RagClient {

    private final WebClient ragWebClient;

    public String query(String question) {
        RagQueryRequest request = new RagQueryRequest(question);

        try {
            RagQueryResponse response = ragWebClient.post()
                    .uri("/rag/query")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RagQueryResponse.class)
                    .block();

            return response.answer();

        } catch (WebClientResponseException e) {
            // 파이썬 쪽 detail 메시지를 그대로 전달
            String detail = e.getResponseBodyAsString();
            throw new ResponseStatusException(
                    HttpStatus.valueOf(e.getStatusCode().value()),
                    detail.isBlank() ? "RAG 서버 오류" : detail
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RAG 서버에 연결할 수 없습니다."
            );
        }
    }

    record RagQueryRequest(String question) {}
    record RagQueryResponse(String answer) {}
}