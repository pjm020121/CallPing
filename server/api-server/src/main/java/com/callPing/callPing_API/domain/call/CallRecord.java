package com.callPing.callPing_API.domain.call;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; // User 엔티티와 연관관계 맵핑 필요 (@ManyToOne)
    private String callerName;
    private String fileName;

    // 분석 상태 - 대기중 / 분석중 / 완료 / 실패 등 (Enum)
    private String analysisStatus;

    // AI 분석 결과 필드
    @Column(columnDefinition = "TEXT")
    private String summary;
    private String speakerResult;
    @Column(columnDefinition = "TEXT")
    private String expression;
    @Column(columnDefinition = "TEXT")
    private String feedback;

    private OffsetDateTime uploadTime;

    // 1:N 관계
    @OneToMany(mappedBy = "callRecord", cascade = CascadeType.ALL)
    private List<CallTranscript> transcripts = new ArrayList<>();
}