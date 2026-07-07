package com.callPing.callPing_API.dto.response.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 채팅방 생성/목록 조회 응답.
 * - POST /chats/rooms (새 채팅방 생성)
 * - GET /chats/rooms (과거 채팅방 목록, List<ChatRoomResponseDTO>로 반환)
 */
@Getter
@AllArgsConstructor
public class ChatRoomResponseDTO {
    private Long roomId;
    private String title;
    private OffsetDateTime createdAt;
}
