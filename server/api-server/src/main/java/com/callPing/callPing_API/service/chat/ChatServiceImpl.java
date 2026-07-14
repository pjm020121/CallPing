package com.callPing.callPing_API.service.chat;

import com.callPing.callPing_API.client.rag.RagClient;
import com.callPing.callPing_API.domain.chat.ChatHistory;
import com.callPing.callPing_API.domain.chat.ChatRoom;
import com.callPing.callPing_API.dto.request.chat.ChatRequestDTO;
import com.callPing.callPing_API.dto.request.chat.ChatStopRequestDTO;
import com.callPing.callPing_API.dto.response.chat.*;
import com.callPing.callPing_API.repository.chat.ChatHistoryRepository;
import com.callPing.callPing_API.repository.chat.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final RagClient ragClient;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private static final String NO_ANSWER_MESSAGE = "AI 답변 생성이 시작되기 전에 중단되었습니다.";

    @Override
    public ChatRoomResponseDTO createRoom() {
        ChatRoom room = new ChatRoom();
        room.setUserId(1L); // TODO: 로그인 붙으면 SecurityContext에서 꺼내서 세팅
        ChatRoom saved = chatRoomRepository.save(room);

        return new ChatRoomResponseDTO(
                saved.getRoomId(),
                saved.getTitle(),
                saved.getCreatedAt()
        );
    }

    @Override
    public List<ChatRoomResponseDTO> getRooms() {
        List<ChatRoomResponseDTO> rooms = chatRoomRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(room -> new ChatRoomResponseDTO(
                        room.getRoomId(), room.getTitle(), room.getCreatedAt()))
                .toList();

        if (rooms.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "과거 대화 내역이 존재하지 않습니다.");
        }

        return rooms;
    }

    @Override
    public List<ChatRoomDetailResponseDTO> getRoomDetail(Long roomId) {
        chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."));

        return chatHistoryRepository.findAllByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(chat -> new ChatRoomDetailResponseDTO(
                        chat.getChatId(),
                        chat.getQuestion(),
                        chat.getAnswer(),
                        chat.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public ChatResponseDTO askQuestion(ChatRequestDTO request) {
        ChatRoom room = chatRoomRepository.findById(request.roomId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."));

        String answer = ragClient.query(request.question());

        boolean isFirstQuestion = chatHistoryRepository.countByRoomId(room.getRoomId()) == 0;
        if (isFirstQuestion) {
            room.setTitle(request.question());
            chatRoomRepository.save(room);
        }

        ChatHistory chat = new ChatHistory();
        chat.setRoomId(room.getRoomId());
        chat.setQuestion(request.question());
        chat.setAnswer(answer);

        ChatHistory saved = chatHistoryRepository.save(chat);

        return new ChatResponseDTO(
                saved.getChatId(),
                room.getRoomId(),
                answer,
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public void stopGeneration(Long roomId, ChatStopRequestDTO request) {

        ChatHistory chat = chatHistoryRepository.findById(request.chatId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 채팅입니다."));

        if (!chat.getRoomId().equals(roomId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "해당 채팅방의 채팅이 아닙니다.");
        }

        String finalAnswer = (request.answer() == null || request.answer().isBlank())
                ? NO_ANSWER_MESSAGE
                : request.answer();

        chat.setAnswer(finalAnswer);
        chatHistoryRepository.save(chat);
    }
}
