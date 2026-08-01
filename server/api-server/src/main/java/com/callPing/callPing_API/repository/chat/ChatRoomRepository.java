package com.callPing.callPing_API.repository.chat;

import com.callPing.callPing_API.domain.chat.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}