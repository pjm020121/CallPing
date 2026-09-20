package com.callPing.callPing_API.repository.call;

import com.callPing.callPing_API.domain.call.CallRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {
    // List -> Page 로 변경하고 파라미터에 Pageable 추가
    Page<CallRecord> findAllByUserIdOrderByUploadTimeDesc(Long userId, Pageable pageable);
}