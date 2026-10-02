package com.callPing.callPing_API.dto.request.call;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CallRecordUpdateRequestDTO(
        @JsonAlias({"fileName", "file_name"}) String fileName,
        @JsonAlias({"callerName", "caller_name"}) String callerName,
        String summary
) {}