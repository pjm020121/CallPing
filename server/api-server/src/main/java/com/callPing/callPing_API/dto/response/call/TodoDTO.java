package com.callPing.callPing_API.dto.response.call;

public record TodoDTO(
        Long todoId,
        String taskType,
        String description,
        String priority,
        String status
) {}