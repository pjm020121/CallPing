/*
package com.callPing.callPing_API.service.chat;

import com.callPing.callPing_API.client.rag.RagClient;
import com.callPing.callPing_API.domain.chat.ChatHistory;
import com.callPing.callPing_API.domain.chat.ChatRoom;
import com.callPing.callPing_API.dto.request.chat.ChatRequestDTO;
import com.callPing.callPing_API.dto.request.chat.ChatStopRequestDTO;
import com.callPing.callPing_API.dto.response.chat.*;
import com.callPing.callPing_API.exception.ApiException;
import com.callPing.callPing_API.repository.chat.ChatHistoryRepository;
import com.callPing.callPing_API.repository.chat.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int TITLE_MAX_LEN = 100;
    private final RagClient ragClient;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private static final String NO_ANSWER_MESSAGE = "AI 답변 생성이 시작되기 전에 중단되었습니다.";

    @Override
    @Transactional   // ← 추가됨
    public ChatRoomResponseDTO createRoom(Long currentUserId) {

        // 추가됨: 새 방 생성 전, 이 유저의 빈 방들 먼저 삭제
        chatRoomRepository.deleteEmptyRoomsByUserId(currentUserId);

        ChatRoom room = new ChatRoom();
        room.setUserId(currentUserId);
        ChatRoom saved = chatRoomRepository.save(room);

        return new ChatRoomResponseDTO(
                saved.getRoomId(),
                saved.getTitle(),
                saved.getCreatedAt()
        );
    }

    @Override
    public List<ChatRoomResponseDTO> getRooms(Long currentUserId) {
        List<ChatRoomResponseDTO> rooms = chatRoomRepository.findAllByUserIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(room -> new ChatRoomResponseDTO(
                        room.getRoomId(), room.getTitle(), room.getCreatedAt()))
                .toList();

        if (rooms.isEmpty()) {
            throw new ApiException(404, "CHAT_ERR_NO_ROOMS", "과거 대화 내역이 존재하지 않습니다.");
        }

        return rooms;
    }

    @Override
    public List<ChatRoomDetailResponseDTO> getRoomDetail(Long roomId, Long currentUserId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅방 내역을 볼 수 있는 권한이 없습니다.");
        }

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
    @Transactional
    public ChatResponseDTO askQuestion(ChatRequestDTO request, Long currentUserId) {

        ChatRoom room = chatRoomRepository.findById(request.roomId())
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅방에 질문할 수 있는 권한이 없습니다.");
        }

        String answer = ragClient.query(request.question());

        boolean isFirstQuestion = chatHistoryRepository.countByRoomId(room.getRoomId()) == 0;
        if (isFirstQuestion) {
            String question = request.question();
            String title = question.length() > TITLE_MAX_LEN
                    ? question.substring(0, TITLE_MAX_LEN) + "..."
                    : question;
            room.setTitle(title);
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
    public void stopGeneration(Long roomId, ChatStopRequestDTO request, Long currentUserId) {

        ChatHistory chat = chatHistoryRepository.findById(request.chatId())
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_NOT_FOUND", "존재하지 않는 채팅입니다."));

        if (!chat.getRoomId().equals(roomId)) {
            throw new ApiException(
                    400, "CHAT_ERR_ROOM_MISMATCH", "해당 채팅방의 채팅이 아닙니다.");
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅에 대한 권한이 없습니다.");
        }

        String finalAnswer = (request.answer() == null || request.answer().isBlank())
                ? NO_ANSWER_MESSAGE
                : request.answer();

        chat.setAnswer(finalAnswer);
        chatHistoryRepository.save(chat);
    }

    @Override
    @Transactional
    public ChatDeleteResponseDTO deleteChat(Long chatId, Long currentUserId) {

        ChatHistory chat = chatHistoryRepository.findById(chatId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_NOT_FOUND", "존재하지 않는 채팅입니다."));

        ChatRoom room = chatRoomRepository.findById(chat.getRoomId())
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅을 삭제할 수 있는 권한이 없습니다.");
        }

        Long roomId = chat.getRoomId();

        chatHistoryRepository.deleteById(chatId);

        boolean roomDeleted = false;
        if (chatHistoryRepository.countByRoomId(roomId) == 0) {
            chatRoomRepository.deleteById(roomId);
            roomDeleted = true;
        }

        return new ChatDeleteResponseDTO(chatId, roomId, roomDeleted);
    }

    @Override
    @Transactional
    public ChatRoomDeleteResponseDTO deleteRoom(Long roomId, Long currentUserId) {

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅방을 삭제할 수 있는 권한이 없습니다.");
        }

        chatHistoryRepository.deleteAllByRoomId(roomId);
        chatRoomRepository.deleteById(roomId);

        return new ChatRoomDeleteResponseDTO(roomId);
    }

}
*/

package com.callPing.callPing_API.service.chat;

import com.callPing.callPing_API.client.rag.RagClient;
import com.callPing.callPing_API.domain.chat.ChatHistory;
import com.callPing.callPing_API.domain.chat.ChatRoom;
import com.callPing.callPing_API.dto.request.chat.ChatRequestDTO;
import com.callPing.callPing_API.dto.request.chat.ChatStopRequestDTO;
import com.callPing.callPing_API.dto.response.chat.*;
import com.callPing.callPing_API.exception.ApiException;
import com.callPing.callPing_API.repository.chat.ChatHistoryRepository;
import com.callPing.callPing_API.repository.chat.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int TITLE_MAX_LEN = 100;
    private final RagClient ragClient;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private static final String NO_ANSWER_MESSAGE = "AI 답변 생성이 시작되기 전에 중단되었습니다.";

    @Override
    @Transactional   // ← 추가됨
    public ChatRoomResponseDTO createRoom(Long currentUserId) {

        // 추가됨: 새 방 생성 전, 이 유저의 빈 방들 먼저 삭제
        chatRoomRepository.deleteEmptyRoomsByUserId(currentUserId);

        ChatRoom room = new ChatRoom();
        room.setUserId(currentUserId);
        ChatRoom saved = chatRoomRepository.save(room);

        return new ChatRoomResponseDTO(
                saved.getRoomId(),
                saved.getTitle(),
                saved.getCreatedAt()
        );
    }

    @Override
    public List<ChatRoomResponseDTO> getRooms(Long currentUserId) {
        List<ChatRoomResponseDTO> rooms = chatRoomRepository.findAllByUserIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(room -> new ChatRoomResponseDTO(
                        room.getRoomId(), room.getTitle(), room.getCreatedAt()))
                .toList();

        if (rooms.isEmpty()) {
            throw new ApiException(404, "CHAT_ERR_NO_ROOMS", "과거 대화 내역이 존재하지 않습니다.");
        }

        return rooms;
    }

    @Override
    public List<ChatRoomDetailResponseDTO> getRoomDetail(Long roomId, Long currentUserId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅방 내역을 볼 수 있는 권한이 없습니다.");
        }

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
    @Transactional
    public ChatResponseDTO askQuestion(ChatRequestDTO request, Long currentUserId) {

        ChatRoom room = chatRoomRepository.findById(request.roomId())
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅방에 질문할 수 있는 권한이 없습니다.");
        }

        // 같은 채팅방의 전체 대화를 맥락으로 함께 전달 (현재 질문은 아직 저장 전이라 포함되지 않음)
        List<RagClient.RagTurn> history = buildRagHistory(room.getRoomId());
        String answer = ragClient.query(request.question(), history);

        boolean isFirstQuestion = chatHistoryRepository.countByRoomId(room.getRoomId()) == 0;
        if (isFirstQuestion) {
            String question = request.question();
            String title = question.length() > TITLE_MAX_LEN
                    ? question.substring(0, TITLE_MAX_LEN) + "..."
                    : question;
            room.setTitle(title);
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

    /**
     * 해당 채팅방의 전체 질문·답변을 RAG 서버 history 형식으로 변환 (오래된 것 → 최근 순)
     * - 길이 압축(예전 답변 생략, 글자수 상한)은 RAG 서버에서 처리
     * - 생성 중단으로 남은 안내 문구는 맥락에서 제외
     */
    private List<RagClient.RagTurn> buildRagHistory(Long roomId) {
        List<ChatHistory> all = chatHistoryRepository.findAllByRoomIdOrderByCreatedAtAsc(roomId);

        List<RagClient.RagTurn> history = new ArrayList<>();
        for (ChatHistory chat : all) {
            history.add(new RagClient.RagTurn("user", chat.getQuestion()));
            String answer = chat.getAnswer();
            if (answer != null && !answer.isBlank() && !NO_ANSWER_MESSAGE.equals(answer)) {
                history.add(new RagClient.RagTurn("assistant", answer));
            }
        }
        return history;
    }

    @Override
    @Transactional
    public void stopGeneration(Long roomId, ChatStopRequestDTO request, Long currentUserId) {

        ChatHistory chat = chatHistoryRepository.findById(request.chatId())
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_NOT_FOUND", "존재하지 않는 채팅입니다."));

        if (!chat.getRoomId().equals(roomId)) {
            throw new ApiException(
                    400, "CHAT_ERR_ROOM_MISMATCH", "해당 채팅방의 채팅이 아닙니다.");
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅에 대한 권한이 없습니다.");
        }

        String finalAnswer = (request.answer() == null || request.answer().isBlank())
                ? NO_ANSWER_MESSAGE
                : request.answer();

        chat.setAnswer(finalAnswer);
        chatHistoryRepository.save(chat);
    }

    @Override
    @Transactional
    public ChatDeleteResponseDTO deleteChat(Long chatId, Long currentUserId) {

        ChatHistory chat = chatHistoryRepository.findById(chatId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_NOT_FOUND", "존재하지 않는 채팅입니다."));

        ChatRoom room = chatRoomRepository.findById(chat.getRoomId())
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅을 삭제할 수 있는 권한이 없습니다.");
        }

        Long roomId = chat.getRoomId();

        chatHistoryRepository.deleteById(chatId);

        boolean roomDeleted = false;
        if (chatHistoryRepository.countByRoomId(roomId) == 0) {
            chatRoomRepository.deleteById(roomId);
            roomDeleted = true;
        }

        return new ChatDeleteResponseDTO(chatId, roomId, roomDeleted);
    }

    @Override
    @Transactional
    public ChatRoomDeleteResponseDTO deleteRoom(Long roomId, Long currentUserId) {

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(
                        404, "CHAT_ERR_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다."));

        if (!room.getUserId().equals(currentUserId)) {
            throw new ApiException(403, "CHAT_ERR_ACCESS_DENIED", "해당 채팅방을 삭제할 수 있는 권한이 없습니다.");
        }

        chatHistoryRepository.deleteAllByRoomId(roomId);
        chatRoomRepository.deleteById(roomId);

        return new ChatRoomDeleteResponseDTO(roomId);
    }

}