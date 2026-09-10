package com.sparta.learning.application.content;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.persistence.repository.DiagnosisResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceSelectionServiceTest {

    @Mock
    private DiagnosisResultRepository diagnosisResultRepository;

    @Mock
    private LearningResourceCandidateOrchestrator candidateOrchestrator;

    private LearningResourceSelectionService selectionService;

    @BeforeEach
    void setUp() {
        selectionService = new LearningResourceSelectionService(
                diagnosisResultRepository,
                new LearningResourceRecommendationPolicy(),
                candidateOrchestrator
        );
    }

    @Test
    @DisplayName("ENTRY 피드백은 ENTRY의 경고와 위반 진단만 추천 대상으로 사용한다")
    void selectsOnlyWarningAndViolationFromAllowedPhase() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        DiagnosisResult pass = diagnosis("STOP_LOSS_SET", DiagnosisPhase.ENTRY, DiagnosisStatus.PASS, "손절가를 설정했습니다.");
        DiagnosisResult tradeWarning = diagnosis("SELL_BELOW_STOP_LOSS", DiagnosisPhase.TRADE, DiagnosisStatus.WARNING, "늦게 매도했습니다.");
        DiagnosisResult entryWarning = diagnosis("HIGH_CHASING_BUY", DiagnosisPhase.ENTRY, DiagnosisStatus.WARNING, "고점 부근에서 매수했습니다.");
        LearningResource video = resource(ResourceProvider.YOUTUBE, "video-1");

        when(diagnosisResultRepository.findAllByPositionIdAndUserIdOrderByIdDesc(positionId, userId))
                .thenReturn(List.of(pass, tradeWarning, entryWarning));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        )).thenReturn(List.of(video));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.DOCUMENT,
                1
        )).thenReturn(List.of());

        List<SelectedLearningResource> result = selectionService.select(
                userId,
                positionId,
                FeedbackType.ENTRY_FEEDBACK
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().resource()).isSameAs(video);
        assertThat(result.getFirst().ruleCode()).isEqualTo("HIGH_CHASING_BUY");
        assertThat(result.getFirst().recommendationReason()).contains("고점 부근에서 매수했습니다.");
        verify(candidateOrchestrator, never()).findOrRefreshCandidates(
                userId,
                positionId,
                "SELL_BELOW_STOP_LOSS",
                ResourceType.VIDEO,
                2
        );
    }

    @Test
    @DisplayName("위반 진단을 경고보다 우선하고 서로 다른 규칙에 후보를 먼저 배분한다")
    void prioritizesViolationAndDistributesAcrossRules() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        DiagnosisResult warning = diagnosis("HIGH_CHASING_BUY", DiagnosisPhase.ENTRY, DiagnosisStatus.WARNING, "고점 매수 경고");
        DiagnosisResult violation = diagnosis("SELL_BELOW_STOP_LOSS", DiagnosisPhase.TRADE, DiagnosisStatus.VIOLATION, "손절가 위반");
        LearningResource violationFirst = resource(ResourceProvider.YOUTUBE, "violation-1");
        LearningResource violationSecond = resource(ResourceProvider.YOUTUBE, "violation-2");
        LearningResource warningFirst = resource(ResourceProvider.YOUTUBE, "warning-1");

        when(diagnosisResultRepository.findAllByPositionIdAndUserIdOrderByIdDesc(positionId, userId))
                .thenReturn(List.of(warning, violation));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "SELL_BELOW_STOP_LOSS",
                ResourceType.VIDEO,
                3
        )).thenReturn(List.of(violationFirst, violationSecond));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                3
        )).thenReturn(List.of(warningFirst));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "SELL_BELOW_STOP_LOSS",
                ResourceType.DOCUMENT,
                2
        )).thenReturn(List.of());
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.DOCUMENT,
                2
        )).thenReturn(List.of());

        List<SelectedLearningResource> result = selectionService.select(
                userId,
                positionId,
                FeedbackType.ON_DEMAND_FEEDBACK
        );

        assertThat(result).extracting(selection -> selection.resource().getExternalId())
                .containsExactly("violation-1", "warning-1", "violation-2");
    }

    @Test
    @DisplayName("서로 다른 규칙에서 같은 외부 자료가 조회되면 한 번만 선택한다")
    void removesDuplicateExternalResourcesAcrossRules() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        DiagnosisResult first = diagnosis("HIGH_CHASING_BUY", DiagnosisPhase.ENTRY, DiagnosisStatus.WARNING, "첫 번째 경고");
        DiagnosisResult second = diagnosis("SHORT_TERM_SURGE_BUY", DiagnosisPhase.ENTRY, DiagnosisStatus.WARNING, "두 번째 경고");
        LearningResource duplicatedFirst = resource(ResourceProvider.YOUTUBE, "same-video");
        LearningResource duplicatedSecond = resource(ResourceProvider.YOUTUBE, "same-video");
        LearningResource unique = resource(ResourceProvider.YOUTUBE, "unique-video");

        when(diagnosisResultRepository.findAllByPositionIdAndUserIdOrderByIdDesc(positionId, userId))
                .thenReturn(List.of(first, second));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        )).thenReturn(List.of(duplicatedFirst));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "SHORT_TERM_SURGE_BUY",
                ResourceType.VIDEO,
                2
        )).thenReturn(List.of(duplicatedSecond, unique));
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.DOCUMENT,
                1
        )).thenReturn(List.of());
        when(candidateOrchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "SHORT_TERM_SURGE_BUY",
                ResourceType.DOCUMENT,
                1
        )).thenReturn(List.of());

        List<SelectedLearningResource> result = selectionService.select(
                userId,
                positionId,
                FeedbackType.ENTRY_FEEDBACK
        );

        assertThat(result).extracting(selection -> selection.resource().getExternalId())
                .containsExactly("same-video", "unique-video");
    }

    private DiagnosisResult diagnosis(
            String ruleCode,
            DiagnosisPhase phase,
            DiagnosisStatus status,
            String message
    ) {
        return DiagnosisResult.builder()
                .diagnosisKey(UUID.randomUUID().toString())
                .userId(UUID.randomUUID())
                .positionId(UUID.randomUUID())
                .diagnosisPhase(phase)
                .ruleCode(ruleCode)
                .ruleVersion(1)
                .result(status)
                .evidence(JsonNodeFactory.instance.objectNode().put("message", message))
                .build();
    }

    private LearningResource resource(ResourceProvider provider, String externalId) {
        LearningResource resource = mock(LearningResource.class);
        when(resource.getProvider()).thenReturn(provider);
        when(resource.getExternalId()).thenReturn(externalId);
        return resource;
    }
}
