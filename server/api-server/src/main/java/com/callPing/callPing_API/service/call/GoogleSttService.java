package com.callPing.callPing_API.service.call;

import com.callPing.callPing_API.domain.call.CallRecord;
import com.callPing.callPing_API.domain.call.CallTranscript;
import com.google.cloud.speech.v1.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleSttService {

    // GCS에 업로드된 파일 URI (예: gs://my-bucket/audio.wav)를 받아 분석합니다.
    public List<CallTranscript> transcribeAudio(String gcsUri, CallRecord callRecord) throws Exception {

        try (SpeechClient speechClient = SpeechClient.create()) {

            // 1. 중간 보고에 맞춘 STT 환경 설정
            SpeakerDiarizationConfig diarizationConfig = SpeakerDiarizationConfig.newBuilder()
                    .setEnableSpeakerDiarization(true)
                    // 화자 수는 지정하지 않음 (STT 자동 판단)
                    .build();

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16) // TODO: 실제 파일 포맷에 맞게 수정
                    .setLanguageCode("ko-KR")
                    .setEnableAutomaticPunctuation(true) // 구두점 자동 생성 (문장 묶기용)
                    .setEnableWordTimeOffsets(true)      // 타임스탬프 활성화
                    .setDiarizationConfig(diarizationConfig)
                    .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setUri(gcsUri) // 반드시 GCS URI 사용
                    .build();

            // 2. 비동기(LongRunning) 요청 호출
            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
                    speechClient.longRunningRecognizeAsync(config, audio);

            // 완료될 때까지 대기 (스레드 블로킹되지만, 어차피 @Async 백그라운드 스레드라 괜찮음)
            LongRunningRecognizeResponse longRunningResponse = response.get();

            // 3. 단어 -> 문장 조합 로직 (Word Grouping)
            return groupWordsIntoSentences(longRunningResponse, callRecord);
        }
    }

    private List<CallTranscript> groupWordsIntoSentences(LongRunningRecognizeResponse response, CallRecord callRecord) {
        List<CallTranscript> transcripts = new ArrayList<>();

        // 결과가 비어있으면 리턴
        if (response.getResultsList().isEmpty()) return transcripts;

        // 화자 분리 결과는 마지막 Result의 마지막 Alternative에 담겨 옵니다. (구글 STT V1 특성)
        SpeechRecognitionResult lastResult = response.getResults(response.getResultsCount() - 1);
        SpeechRecognitionAlternative alternative = lastResult.getAlternatives(0);

        StringBuilder currentSentence = new StringBuilder();
        int currentSpeakerTag = -1;
        String startTime = "00:00"; // 첫 단어의 시작 시간

        for (WordInfo wordInfo : alternative.getWordsList()) {
            int wordSpeakerTag = wordInfo.getSpeakerTag();
            String word = wordInfo.getWord();

            // 첫 단어 세팅
            if (currentSpeakerTag == -1) {
                currentSpeakerTag = wordSpeakerTag;
                startTime = formatTime(wordInfo.getStartTime().getSeconds());
            }

            // [문장을 끊는 조건]
            // 1. 화자가 변경되었을 때
            // 2. 단어 끝에 구두점(. ? !)이 있을 때 (enableAutomaticPunctuation 효과)
            boolean isSpeakerChanged = (currentSpeakerTag != wordSpeakerTag);

            if (isSpeakerChanged) {
                // 기존 화자의 문장 저장
                transcripts.add(createTranscript(callRecord, currentSpeakerTag, startTime, currentSentence.toString()));

                // 새로운 화자로 초기화
                currentSentence.setLength(0);
                currentSpeakerTag = wordSpeakerTag;
                startTime = formatTime(wordInfo.getStartTime().getSeconds());
            }

            currentSentence.append(word).append(" ");

            // 구두점으로 끝났다면 저장하고 초기화 (같은 화자가 계속 말하더라도 문장 단위로 나눔)
            if (word.matches(".*[.?!]$")) {
                transcripts.add(createTranscript(callRecord, currentSpeakerTag, startTime, currentSentence.toString().trim()));
                currentSentence.setLength(0);
                currentSpeakerTag = -1; // 다음 단어에서 새 시작 시간과 화자 세팅하도록 유도
            }
        }

        // 마지막에 남은 텍스트가 있다면 저장
        if (currentSentence.length() > 0) {
            transcripts.add(createTranscript(callRecord, currentSpeakerTag, startTime, currentSentence.toString().trim()));
        }

        return transcripts;
    }

    private CallTranscript createTranscript(CallRecord callRecord, int speakerTag, String timestampOffset, String text) {
        return CallTranscript.builder()
                .callRecord(callRecord)
                .speaker("화자 " + speakerTag)
                .timestampOffset(timestampOffset)
                .text(text.trim())
                .build();
    }

    private String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}