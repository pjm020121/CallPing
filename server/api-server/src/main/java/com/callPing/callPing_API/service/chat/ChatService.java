package com.callPing.callPing_API.service.chat;

import com.callPing.callPing_API.dto.request.chat.ChatRequestDTO;
import com.callPing.callPing_API.dto.request.chat.ChatStopRequestDTO;
import com.callPing.callPing_API.dto.response.chat.*;

import java.util.List;

public interface ChatService {
    ChatRoomResponseDTO createRoom();
    List<ChatRoomResponseDTO> getRooms();
    List<ChatRoomDetailResponseDTO> getRoomDetail(Long roomId);
    ChatResponseDTO askQuestion(ChatRequestDTO request);
    void stopGeneration(Long roomId, ChatStopRequestDTO request);
    ChatDeleteResponseDTO deleteChat(Long chatId);
    ChatRoomDeleteResponseDTO deleteRoom(Long roomId);
}