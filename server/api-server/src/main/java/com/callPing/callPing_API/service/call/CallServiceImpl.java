package com.callPing.callPing_API.service.call;

import com.callPing.callPing_API.domain.call.CallRecord;
import com.callPing.callPing_API.domain.call.CallTranscript;
import com.callPing.callPing_API.dto.response.call.CallRecordResponseDTO;
import com.callPing.callPing_API.repository.call.CallRecordRepository;
import com.callPing.callPing_API.repository.call.CallTranscriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallServiceImpl implements CallService {

    private final CallRecordRepository callRecordRepository;
    private final CallTranscriptRepository callTranscriptRepository;
    private final GoogleSttService googleSttService;
    // private final AiFeedbackService aiFeedbackService;
    private final GcsUploadService gcsUploadService;

    @Override
    @Transactional
    public CallRecordResponseDTO processCallRecord(Long userId, String callerName, MultipartFile file) {

        // DB에 '분석 중'으로 껍데기 생성
        CallRecord callRecord = CallRecord.builder()
                .userId(userId)
                .callerName(callerName)
                .fileName(file.getOriginalFilename())
                .uploadTime(OffsetDateTime.now())
                .analysisStatus("분석중")
                .build();
        callRecordRepository.save(callRecord);

        try {
            // [추가된 부분] GCS에 파일 업로드하고 URI 받아오기!
            String gcsUri = gcsUploadService.uploadAudioFile(file);

            // 비동기로 STT 분석 및 AI 분석 시작 -- AI 설정 완료 후 연결
            // processSttAndAiAsync(callRecord.getId(), gcsUri);

        } catch (Exception e) {
            log.error("GCS 업로드 실패", e);
            throw new RuntimeException("녹음 파일 업로드에 실패했습니다.");
        }

        // 3. 사용자에게는 즉시 200 OK 응답!
        return new CallRecordResponseDTO(
                callRecord.getId(), callRecord.getCallerName(),
                callRecord.getFileName(), callRecord.getUploadTime(),
                callRecord.getAnalysisStatus()
        );
    }


    // @Async 를 통해 별도의 스레드에서 구글 STT와 AI 서버를 호출
    // TODO Ai Service 확정 후 주석 해제
//    @Async
//    @Transactional
//    public void processSttAndAiAsync(Long recordId, String gcsUri) {
//        CallRecord callRecord = callRecordRepository.findById(recordId)
//                .orElseThrow(() -> new IllegalArgumentException("레코드 없음"));
//
//        try {
//            log.info("비동기 분석 시작 - recordId: {}", recordId);
//
//            // 1. Google STT V2 호출 및 단어를 문장으로 병합 (1분 이상 걸릴 수 있음)
//            List<CallTranscript> transcripts = googleSttService.transcribeAudio(gcsUri, callRecord);
//
//            transcripts.forEach(callRecord::addTranscript);
//            callTranscriptRepository.saveAll(transcripts);
//
//            // 2. AI 서버용 문장 리스트 추출
//            List<String> sentences = transcripts.stream()
//                    .map(CallTranscript::getText)
//                    .collect(Collectors.toList());
//
//            // 3. AI 서버에 전송 (응답 15초 대기)
//            String rawAiResponse = aiFeedbackService.getAiFeedback(sentences);
//
//            // 4. AI 텍스트 결과 파싱 로직 적용 (아래의 파싱 메서드 호출)
//            String[] parsedResults = parseAiResponse(rawAiResponse);
//            String expression = parsedResults[0];
//            String feedback = parsedResults[1];
//
//            // 5. DB 상태 업데이트
//            callRecord.updateAiAnalysis(expression, feedback, "완료");
//            log.info("비동기 분석 완료 - recordId: {}", recordId);
//
//        } catch (Exception e) {
//            log.error("비동기 분석 중 에러 발생 - recordId: {}", recordId, e);
//            callRecord.updateStatus("실패");
//        }
//    }

    /**
     * AI 모델이 뱉어낸 문자열을 [표현 분석]과 [개선 피드백]으로 분리하는 파서
     * 반환값: Index 0 (표현 분석), Index 1 (개선 피드백)
     */
    private String[] parseAiResponse(String rawText) {
        String expression = "";
        String feedback = "";

        // 섹션 헤더가 존재하는지 확인
        if (rawText.contains("[표현 분석]") && rawText.contains("[개선 피드백]")) {
            try {
                // "[표현 분석]"과 "[개선 피드백]" 사이의 텍스트 추출
                int expStart = rawText.indexOf("[표현 분석]") + "[표현 분석]".length();
                int fbStart = rawText.indexOf("[개선 피드백]");

                expression = rawText.substring(expStart, fbStart).trim();

                // "[개선 피드백]" 부터 끝까지 텍스트 추출
                feedback = rawText.substring(fbStart + "[개선 피드백]".length()).trim();

            } catch (Exception e) {
                // 파싱 중 문제 생기면 폴백 (전체 텍스트를 feedback에)
                log.warn("AI 텍스트 파싱 오류 발생. 원문 전체를 피드백으로 이동합니다.");
                feedback = rawText;
            }
        } else {
            // 섹션 헤더 없으면 전체를 feedback으로 폴백 (AI 팀 요구사항 반영)
            log.info("AI 응답에 섹션 헤더가 없어 전체 텍스트를 피드백으로 폴백합니다.");
            feedback = rawText;
        }

        return new String[]{expression, feedback};
    }
}