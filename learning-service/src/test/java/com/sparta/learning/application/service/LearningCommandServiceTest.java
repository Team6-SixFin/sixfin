package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.infrastructure.persistence.repository.AiRequestRepository;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningCommandServiceTest {

    @Mock
    private AiClientPort aiClientPort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private AiRequestRepository aiRequestRepository;

    @Mock
    private ExecutionSnapshotRepository executionSnapshotRepository;

    @InjectMocks
    private LearningCommandService learningCommandService;

    @Test
    @DisplayName("요청형 매매 피드백 생성 성공 테스트")
    void createOnDemandFeedback_Success() {
        // given
        UUID positionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionSnapshot mockSnapshot = ExecutionSnapshot.builder()
                .executionId(executionId)
                .positionId(positionId)
                .userId(userId)
                .executedAt(OffsetDateTime.now())
                .build();

        AiFeedbackResponse mockAiResponse = new AiFeedbackResponse(
                "분석 완료",
                "좋은 매매 타이밍이었습니다.",
                List.of("지속 유지")
        );

        given(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .willReturn(Optional.of(mockSnapshot));
        given(feedbackRepository.findByFeedbackKey(anyString()))
                .willReturn(Optional.empty());
        given(feedbackRepository.save(any(Feedback.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(aiClientPort.requestAiFeedback(any(UUID.class), any(FeedbackType.class), anyString()))
                .willReturn(mockAiResponse);

        // when
        AiFeedbackResponse response = learningCommandService.createOnDemandFeedback(positionId, userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("분석 완료");
        verify(aiClientPort, times(1)).requestAiFeedback(any(UUID.class), any(FeedbackType.class), anyString());
        verify(aiRequestRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("이미 존재하는 피드백(feedbackKey)이 있는 경우 AI를 호출하지 않고 기존 결과 반환 (멱등성)")
    void createOnDemandFeedback_Idempotency() throws Exception {
        // given
        UUID positionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        ExecutionSnapshot mockSnapshot = ExecutionSnapshot.builder()
                .executionId(executionId)
                .positionId(positionId)
                .userId(userId)
                .executedAt(OffsetDateTime.now())
                .build();

        AiFeedbackResponse existingResponse = new AiFeedbackResponse(
                "기존 피드백",
                "이미 생성된 내용입니다.",
                List.of("참고") // ✅ List 타입으로 수정
        );
        Feedback existingFeedback = Feedback.builder()
                .feedbackKey("ON_DEMAND_FEEDBACK:" + positionId + ":" + executionId)
                .userId(userId)
                .positionId(positionId)
                .basedOnExecutionId(executionId)
                .feedbackType(FeedbackType.ON_DEMAND_FEEDBACK)
                .build();

        // 리플렉션이나 objectMapper를 통해 content 필드 세팅 (필요시 빌더나 메서드 활용)
        existingFeedback.complete(objectMapper.valueToTree(existingResponse), true, "v1.0");

        given(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .willReturn(Optional.of(mockSnapshot));
        given(feedbackRepository.findByFeedbackKey(anyString()))
                .willReturn(Optional.of(existingFeedback));

        // when
        AiFeedbackResponse response = learningCommandService.createOnDemandFeedback(positionId, userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("기존 피드백");
        // AI 클라이언트가 호출되지 않았음을 검증 (캐싱/멱등성 효과)
        verify(aiClientPort, never()).requestAiFeedback(any(UUID.class), any(FeedbackType.class), anyString());
    }
}