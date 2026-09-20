package com.callPing.callPing_API.repository;

import com.callPing.callPing_API.domain.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    // 1. 특정 사용자의 전체 TODO 목록
    List<Todo> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 2. 특정 통화 기록에서 파생된 TODO 목록
    List<Todo> findByCallRecordId(Long recordId);
}