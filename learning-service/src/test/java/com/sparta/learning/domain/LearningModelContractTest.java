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

class LearningModelContractTest {

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

    @Test
    void coreTableNamesMatchSpecification() {
        assertTableName(ConsumedEvent.class, "consumed_events");
        assertTableName(ExecutionSnapshot.class, "execution_snapshots");
        assertTableName(DiagnosisResult.class, "diagnosis_results");
        assertTableName(Feedback.class, "feedbacks");
        assertTableName(LearningResource.class, "learning_resources");
        assertTableName(AiRequest.class, "ai_requests");
    }

    private void assertTableName(Class<?> entityType, String expectedName) {
        assertThat(entityType.getAnnotation(Table.class).name()).isEqualTo(expectedName);
    }
}
