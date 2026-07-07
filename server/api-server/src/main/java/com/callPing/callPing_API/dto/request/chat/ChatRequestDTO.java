package com.callPing.callPing_API.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RAG 챗봇 질문 요청.
 * - POST /chats
 */
@Getter
@NoArgsConstructor
public class ChatRequestDTO {

    @NotNull(message = "room_id는 필수입니다.")
    private Long roomId;

    @NotBlank(message = "질문 내용은 필수입니다.")
    private String question;
}
