package com.callPing.callPing_API.dto.request.call;

public record CallRecordUpdateRequestDTO(
        String fileName,   // 파일명
        String callerName, // 관계 (발신자 등)
        String summary     // 요약
) {}