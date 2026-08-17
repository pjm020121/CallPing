package com.callPing.callPing_API.domain.call;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallTranscript {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_record_id")
    private CallRecord callRecord;

    private String speaker; // "화자1", "화자2"
    private String timestampOffset; // "00:12"

    @Column(columnDefinition = "TEXT")
    private String text;

    // 소프트 삭제 
    private boolean isDeleted = false;

    // 수정을 위한 메서드
    public void updateText(String text) {
        this.text = text;
    }

    // 삭제를 위한 메서드
    public void markAsDeleted() {
        this.isDeleted = true;
    }
}