package com.callPing.callPing_API.domain;

import com.callPing.callPing_API.domain.call.CallRecord;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "todos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_record_id")
    private CallRecord callRecord; // null 허용

    @Column(nullable = false)
    private String taskType; // ex) "EMAIL_SEND", "SYSTEM_UPDATE", "MANUAL"

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String priority = "MEDIUM";

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public void updateTodo(String status, String description, String priority) {
        if (status != null) this.status = status;
        if (description != null) this.description = description;
        if (priority != null) this.priority = priority;
    }
}