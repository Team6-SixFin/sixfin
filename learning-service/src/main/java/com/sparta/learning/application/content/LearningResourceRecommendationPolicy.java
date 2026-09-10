package com.sparta.learning.application.content;

import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.RuleCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 진단 규칙을 학습 주제와 검색어로 변환하고, 피드백 종류별 추천 개수를 결정
 * 외부 검색 API와 분리해 검색 제공자가 바뀌더라도 교육 정책은 한 곳에서 유지
 */
@Component
public class LearningResourceRecommendationPolicy {

    private final Map<RuleCode, SearchTerms> searchTermsByRuleCode = createSearchTerms();
    private final Map<FeedbackType, RecommendationLimit> limitsByFeedbackType = createLimits();

    /**
     * DiagnosisResult에는 ruleCode가 문자열로 저장되므로 잘못된 값은 예외 대신 추천 제외로 처리
     */
    public Optional<SearchTerms> findSearchTerms(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(searchTermsByRuleCode.get(RuleCode.valueOf(ruleCode)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public RecommendationLimit getLimit(FeedbackType feedbackType) {
        RecommendationLimit limit = limitsByFeedbackType.get(feedbackType);
        if (limit == null) {
            throw new IllegalArgumentException("지원하지 않는 피드백 유형입니다: " + feedbackType);
        }
        return limit;
    }

    private Map<RuleCode, SearchTerms> createSearchTerms() {
        EnumMap<RuleCode, SearchTerms> policies = new EnumMap<>(RuleCode.class);
        policies.put(RuleCode.STOP_LOSS_SET, new SearchTerms(
                "매수 전 손절 계획 설정",
                "주식 매수 전 손절가 설정 방법 투자 원칙",
                "투자 손절 계획 위험 관리 가이드"
        ));
        policies.put(RuleCode.STOP_LOSS_WIDTH, new SearchTerms(
                "손절 폭과 포지션 위험 관리",
                "주식 적정 손절폭 설정 리스크 관리",
                "투자 손절 폭 포지션 위험 관리 문서"
        ));
        policies.put(RuleCode.HIGH_CHASING_BUY, new SearchTerms(
                "고점 추격 매수 예방",
                "주식 고점 추격 매수 위험 예방",
                "고점 추격 매수 투자자 행동 편향 가이드"
        ));
        policies.put(RuleCode.SHORT_TERM_SURGE_BUY, new SearchTerms(
                "단기 급등 후 추격 매수 예방",
                "급등주 추격 매수 위험 투자 원칙",
                "단기 급등 추격 매수 위험 관리 문서"
        ));
        policies.put(RuleCode.REPEATED_HIGH_CHASING_BUY, new SearchTerms(
                "반복되는 고점 추격 매수 교정",
                "반복 고점 매수 습관 교정 투자",
                "반복 추격 매수 행동 편향 개선 가이드"
        ));
        policies.put(RuleCode.SELL_BELOW_STOP_LOSS, new SearchTerms(
                "계획 손절가 이탈 대응",
                "손절가 지키기 매도 원칙 투자 심리",
                "손절가 이탈 투자 원칙 위험 관리 문서"
        ));
        policies.put(RuleCode.STOP_LOSS_ADHERENCE, new SearchTerms(
                "손절 원칙 준수와 거래 회고",
                "주식 손절 원칙 준수 거래 복기",
                "투자 손절 규칙 준수 거래 회고 가이드"
        ));
        policies.put(RuleCode.HIGH_CHASING_FREQUENCY, new SearchTerms(
                "고점 추격 매수 습관 교정",
                "고점 추격 매수 반복 습관 개선",
                "추격 매수 행동 편향 개선 투자 교육 자료"
        ));
        return Map.copyOf(policies);
    }

    private Map<FeedbackType, RecommendationLimit> createLimits() {
        EnumMap<FeedbackType, RecommendationLimit> limits = new EnumMap<>(FeedbackType.class);
        limits.put(FeedbackType.ENTRY_FEEDBACK, new RecommendationLimit(2, 1));
        limits.put(FeedbackType.ON_DEMAND_FEEDBACK, new RecommendationLimit(3, 2));
        limits.put(FeedbackType.POSITION_REVIEW, new RecommendationLimit(3, 2));
        return Map.copyOf(limits);
    }

    public record SearchTerms(
            String topic,
            String youtubeQuery,
            String documentQuery
    ) {
    }

    public record RecommendationLimit(
            int videoLimit,
            int documentLimit
    ) {
    }
}
