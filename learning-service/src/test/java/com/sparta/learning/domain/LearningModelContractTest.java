package com.sparta.learning.domain;

import com.sparta.learning.domain.entity.AiRequest;
import com.sparta.learning.domain.entity.ConsumedEvent;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.AiRequestStatus;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceStatus;
import com.sparta.learning.domain.model.TradeEventType;
import com.sparta.learning.domain.model.TradeType;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코드에 정의된 엔티티와 ENUM이 SA 단계에서 확정한 테이블 명세를 따르는지 검증
 * 명세를 변경할 때 의도하지 않은 DB 계약 변경을 조기에 발견하기 위한 테스트
 */
class LearningModelContractTest {

    // DB에 문자열로 저장되는 ENUM 값과 순서가 최종 명세와 정확히 일치하는지 확인한다.
    @Test
    void enumValuesMatchTableSpecification() {
        assertThat(TradeEventType.values()).containsExactly(
                TradeEventType.BUY_EXECUTED,
                TradeEventType.SELL_EXECUTED,
                TradeEventType.POSITION_CLOSED
        );
        assertThat(TradeType.values()).containsExactly(TradeType.BUY, TradeType.SELL);
        assertThat(DiagnosisPhase.values()).containsExactly(
                DiagnosisPhase.ENTRY, DiagnosisPhase.TRADE, DiagnosisPhase.CLOSE
        );
        assertThat(DiagnosisStatus.values()).containsExactly(
                DiagnosisStatus.PASS,
                DiagnosisStatus.WARNING,
                DiagnosisStatus.VIOLATION,
                DiagnosisStatus.NOT_APPLICABLE
        );
        assertThat(FeedbackType.values()).containsExactly(
                FeedbackType.ENTRY_FEEDBACK,
                FeedbackType.ON_DEMAND_FEEDBACK,
                FeedbackType.POSITION_REVIEW
        );
        assertThat(FeedbackStatus.values()).containsExactly(
                FeedbackStatus.PENDING,
                FeedbackStatus.COMPLETED,
                FeedbackStatus.FAILED
        );
        assertThat(ResourceProvider.values()).containsExactly(ResourceProvider.YOUTUBE);
        assertThat(ResourceStatus.values()).containsExactly(
                ResourceStatus.ACTIVE,
                ResourceStatus.REJECTED,
                ResourceStatus.UNAVAILABLE
        );
        assertThat(AiRequestStatus.values()).containsExactly(
                AiRequestStatus.SUCCESS,
                AiRequestStatus.FAILED,
                AiRequestStatus.FALLBACK
        );
    }

    // 핵심 엔티티의 실제 테이블명이 복수형 테이블 명세와 일치하는지 확인한다.
    @Test
    void coreTableNamesMatchSpecification() {
        assertTableName(ConsumedEvent.class, "consumed_events");
        assertTableName(ExecutionSnapshot.class, "execution_snapshots");
        assertTableName(DiagnosisResult.class, "diagnosis_results");
        assertTableName(Feedback.class, "feedbacks");
        assertTableName(LearningResource.class, "learning_resources");
        assertTableName(AiRequest.class, "ai_requests");
    }

    // 엔티티의 @Table 이름을 읽어 명세에 정의된 이름과 비교한다.
    private void assertTableName(Class<?> entityType, String expectedName) {
        assertThat(entityType.getAnnotation(Table.class).name()).isEqualTo(expectedName);
    }
}
