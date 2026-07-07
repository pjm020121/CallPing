package com.callPing.callPing_API.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * RAG 챗봇 답변 응답.
 * - POST /chats (질문 응답)
 * - POST /chats/rooms/{room_id}/stop (중단 시점까지의 답변)
 *
 * stop 응답엔 created_at이 없어서 nullable로 둠.
 */
@Getter
@AllArgsConstructor
public class ChatResponseDTO {
    private Long chatId;
    private Long roomId;
    private String answer;
    private OffsetDateTime createdAt;
}
