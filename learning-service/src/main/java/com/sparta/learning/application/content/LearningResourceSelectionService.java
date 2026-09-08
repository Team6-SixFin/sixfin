package com.sparta.learning.application.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.persistence.repository.DiagnosisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 진단 심각도와 피드백 유형을 기준으로 실제 피드백에 사용할 학습 자료를 선택 */
@Service
@RequiredArgsConstructor
public class LearningResourceSelectionService {

    private final DiagnosisResultRepository diagnosisResultRepository;
    private final LearningResourceRecommendationPolicy recommendationPolicy;
    private final LearningResourceCandidateOrchestrator candidateOrchestrator;

    public List<SelectedLearningResource> select(
            UUID userId,
            UUID positionId,
            FeedbackType feedbackType
    ) {
        validate(userId, positionId, feedbackType);

        List<DiagnosisResult> diagnoses = prioritizedDiagnoses(userId, positionId, feedbackType);
        if (diagnoses.isEmpty()) {
            return List.of();
        }

        LearningResourceRecommendationPolicy.RecommendationLimit limit =
                recommendationPolicy.getLimit(feedbackType);

        List<SelectedLearningResource> selected = new ArrayList<>();
        selected.addAll(selectByType(
                userId,
                positionId,
                diagnoses,
                ResourceType.VIDEO,
                limit.videoLimit()
        ));
        selected.addAll(selectByType(
                userId,
                positionId,
                diagnoses,
                ResourceType.DOCUMENT,
                limit.documentLimit()
        ));
        return List.copyOf(selected);
    }

    private List<DiagnosisResult> prioritizedDiagnoses(
            UUID userId,
            UUID positionId,
            FeedbackType feedbackType
    ) {
        Set<DiagnosisPhase> allowedPhases = allowedPhases(feedbackType);
        LinkedHashMap<String, DiagnosisResult> diagnosisByRuleCode = new LinkedHashMap<>();

        /** 피드백에 필요한 진단 단계만 남김
            → PASS·NOT_APPLICABLE 제외
            → VIOLATION 우선 정렬
            → 같은 ruleCode 중복 제거
         */
        diagnosisResultRepository.findAllByPositionIdAndUserIdOrderByIdDesc(positionId, userId).stream()
                .filter(diagnosis -> allowedPhases.contains(diagnosis.getDiagnosisPhase()))
                .filter(diagnosis -> isRecommendationTarget(diagnosis.getResult()))
                // 원본 조회 순서가 최신순이고 Stream 정렬은 안정 정렬이므로 같은 심각도에서는 최신 진단이 우선
                .sorted(Comparator.comparingInt(diagnosis -> statusPriority(diagnosis.getResult())))
                .forEach(diagnosis -> diagnosisByRuleCode.putIfAbsent(diagnosis.getRuleCode(), diagnosis));

        return List.copyOf(diagnosisByRuleCode.values());
    }

    private List<SelectedLearningResource> selectByType(
            UUID userId,
            UUID positionId,
            List<DiagnosisResult> diagnoses,
            ResourceType resourceType,
            int limit
    ) {
        if (limit < 1) {
            return List.of();
        }

        List<RuleCandidateGroup> candidateGroups = new ArrayList<>();
        List<SelectedLearningResource> selected = new ArrayList<>();
        Set<String> selectedExternalResources = new LinkedHashSet<>();

        // 심각도가 높은 서로 다른 규칙에 우선 한 자리씩 배분
        for (DiagnosisResult diagnosis : diagnoses) {
            LearningResourceRecommendationPolicy.SearchTerms searchTerms = recommendationPolicy
                    .findSearchTerms(diagnosis.getRuleCode())
                    .orElse(null);
            if (searchTerms == null) {
                continue;
            }

            List<LearningResource> candidates = candidateOrchestrator.findOrRefreshCandidates(
                    userId,
                    positionId,
                    diagnosis.getRuleCode(),
                    resourceType,
                    limit
            );
            if (candidates.isEmpty()) {
                continue;
            }

            RuleCandidateGroup group = new RuleCandidateGroup(diagnosis, searchTerms, candidates);
            candidateGroups.add(group);
            addFirstUnused(group, selectedExternalResources, selected);

            if (selected.size() >= limit) {
                return List.copyOf(selected);
            }
        }

        // 규칙별 한 개씩 배분한 뒤 빈 자리가 있으면 각 규칙의 다음 후보를 순서대로
        for (int candidateIndex = 0; selected.size() < limit; candidateIndex++) {
            boolean candidateExistsAtIndex = false;

            for (RuleCandidateGroup group : candidateGroups) {
                if (candidateIndex >= group.candidates().size()) {
                    continue;
                }

                candidateExistsAtIndex = true;
                addIfUnused(
                        group,
                        group.candidates().get(candidateIndex),
                        selectedExternalResources,
                        selected
                );
                if (selected.size() >= limit) {
                    break;
                }
            }

            if (!candidateExistsAtIndex) {
                break;
            }
        }

        return List.copyOf(selected);
    }

    private void addFirstUnused(
            RuleCandidateGroup group,
            Set<String> selectedExternalResources,
            List<SelectedLearningResource> selected
    ) {
        group.candidates().stream()
                .filter(candidate -> selectedExternalResources.add(externalResourceKey(candidate)))
                .findFirst()
                .ifPresent(candidate -> selected.add(toSelection(group, candidate)));
    }

    private void addIfUnused(
            RuleCandidateGroup group,
            LearningResource candidate,
            Set<String> selectedExternalResources,
            List<SelectedLearningResource> selected
    ) {
        if (selectedExternalResources.add(externalResourceKey(candidate))) {
            selected.add(toSelection(group, candidate));
        }
    }

    private SelectedLearningResource toSelection(
            RuleCandidateGroup group,
            LearningResource resource
    ) {
        return new SelectedLearningResource(
                resource,
                group.diagnosis().getRuleCode(),
                recommendationReason(group.diagnosis(), group.searchTerms())
        );
    }

    private String recommendationReason(
            DiagnosisResult diagnosis,
            LearningResourceRecommendationPolicy.SearchTerms searchTerms
    ) {
        JsonNode evidence = diagnosis.getEvidence();
        if (evidence != null) {
            String message = evidence.path("message").asText("");
            if (!message.isBlank()) {
                return message + " 관련 습관을 점검할 수 있는 자료입니다.";
            }
        }
        return searchTerms.topic() + " 진단과 관련된 학습 자료입니다.";
    }

    private String externalResourceKey(LearningResource resource) {
        return resource.getProvider().name() + ":" + resource.getExternalId();
    }

    private Set<DiagnosisPhase> allowedPhases(FeedbackType feedbackType) {
        return switch (feedbackType) {
            case ENTRY_FEEDBACK -> EnumSet.of(DiagnosisPhase.ENTRY);
            case ON_DEMAND_FEEDBACK -> EnumSet.of(DiagnosisPhase.ENTRY, DiagnosisPhase.TRADE);
            case POSITION_REVIEW -> EnumSet.allOf(DiagnosisPhase.class);
        };
    }

    private boolean isRecommendationTarget(DiagnosisStatus status) {
        return status == DiagnosisStatus.WARNING || status == DiagnosisStatus.VIOLATION;
    }

    // Violation이 Warning보다 우선순위가 더 높음
    private int statusPriority(DiagnosisStatus status) {
        return status == DiagnosisStatus.VIOLATION ? 0 : 1;
    }

    private void validate(UUID userId, UUID positionId, FeedbackType feedbackType) {
        if (userId == null || positionId == null || feedbackType == null) {
            throw new IllegalArgumentException("학습 자료 선택 식별값은 비어 있을 수 없습니다.");
        }
    }

    private record RuleCandidateGroup(
            DiagnosisResult diagnosis,
            LearningResourceRecommendationPolicy.SearchTerms searchTerms,
            List<LearningResource> candidates
    ) {
    }
}
