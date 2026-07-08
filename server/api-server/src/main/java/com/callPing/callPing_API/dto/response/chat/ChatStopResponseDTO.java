package com.callPing.callPing_API.dto.response.chat;

/**
 * AI 답변 생성 중단 응답 (중단 시점까지의 답변만 반환, created_at 없음).
 * - POST /chats/rooms/{room_id}/stop
 */
public record ChatStopResponseDTO(
        Long chatId,
        Long roomId,
        String answer
) {}