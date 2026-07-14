package com.callPing.callPing_API.controller.chat;

import com.callPing.callPing_API.dto.request.chat.ChatRequestDTO;
import com.callPing.callPing_API.dto.request.chat.ChatStopRequestDTO;
import com.callPing.callPing_API.dto.response.chat.*;
import com.callPing.callPing_API.service.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 새 채팅방 생성 */
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponseDTO> createRoom() {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createRoom());
    }

    /** 과거 채팅방 목록 조회 */
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponseDTO>> getRooms() {
        return ResponseEntity.ok(chatService.getRooms());
    }

    /** 특정 채팅방의 질문/답변 타임라인 조회 */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<List<ChatRoomDetailResponseDTO>> getRoomDetail(
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(chatService.getRoomDetail(roomId));
    }

    /** RAG 챗봇 질문 요청 → Python 서버 호출 */
    @PostMapping
    public ResponseEntity<ChatResponseDTO> askQuestion(
            @Valid @RequestBody ChatRequestDTO request
    ) {
        return ResponseEntity.ok(chatService.askQuestion(request));
    }

    /** AI 답변 생성 중단 */
    @PostMapping("/rooms/{roomId}/stop")
    public ResponseEntity<Void> stopGeneration(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatStopRequestDTO request
    ) {
        chatService.stopGeneration(roomId, request);
        return ResponseEntity.ok().build();
    }
}