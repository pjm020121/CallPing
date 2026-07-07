package com.callPing.callPing_API.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 특정 채팅방의 질문/답변 타임라인 항목.
 * - GET /chats/rooms/{room_id} (List<ChatRoomDetailResponseDTO>로 반환)
 */
@Getter
@AllArgsConstructor
public class ChatRoomDetailResponseDTO {
    private Long chatId;
    private String question;
    private String answer;
    private OffsetDateTime createdAt;
}
