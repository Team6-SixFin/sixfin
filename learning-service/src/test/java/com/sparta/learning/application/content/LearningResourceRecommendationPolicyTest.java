package com.sparta.learning.application.content;

import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.RuleCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningResourceRecommendationPolicyTest {

    private final LearningResourceRecommendationPolicy policy = new LearningResourceRecommendationPolicy();

    @Test
    void definesSearchTermsForEveryDiagnosisRule() {
        for (RuleCode ruleCode : RuleCode.values()) {
            assertThat(policy.findSearchTerms(ruleCode.name()))
                    .as("ruleCode=%s", ruleCode)
                    .isPresent()
                    .get()
                    .satisfies(terms -> {
                        assertThat(terms.topic()).isNotBlank();
                        assertThat(terms.youtubeQuery()).isNotBlank();
                        assertThat(terms.documentQuery()).isNotBlank();
                    });
        }
    }

    @Test
    void ignoresUnknownOrEmptyRuleCode() {
        assertThat(policy.findSearchTerms("UNKNOWN_RULE")).isEmpty();
        assertThat(policy.findSearchTerms(" ")).isEmpty();
        assertThat(policy.findSearchTerms(null)).isEmpty();
    }

    @Test
    void limitsEntryFeedbackToTwoVideosAndOneDocument() {
        LearningResourceRecommendationPolicy.RecommendationLimit limit =
                policy.getLimit(FeedbackType.ENTRY_FEEDBACK);

        assertThat(limit.videoLimit()).isEqualTo(2);
        assertThat(limit.documentLimit()).isEqualTo(1);
    }

    @Test
    void allowsThreeVideosAndTwoDocumentsForDetailedFeedback() {
        LearningResourceRecommendationPolicy.RecommendationLimit onDemand =
                policy.getLimit(FeedbackType.ON_DEMAND_FEEDBACK);
        LearningResourceRecommendationPolicy.RecommendationLimit positionReview =
                policy.getLimit(FeedbackType.POSITION_REVIEW);

        assertThat(onDemand.videoLimit()).isEqualTo(3);
        assertThat(onDemand.documentLimit()).isEqualTo(2);
        assertThat(positionReview).isEqualTo(onDemand);
    }
}
