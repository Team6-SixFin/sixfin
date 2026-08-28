package com.sparta.learning.domain.entity;

import com.sparta.learning.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "feedback_resources",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_feedback_resource",
                columnNames = {"feedback_id", "learning_resource_id"}
        ),
        indexes = {
                @Index(name = "idx_feedback_resource_feedback", columnList = "feedback_id"),
                @Index(name = "idx_feedback_resource_learning", columnList = "learning_resource_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackResource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learning_resource_id", nullable = false)
    private LearningResource learningResource;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "recommendation_reason", columnDefinition = "text")
    private String recommendationReason;

    @Builder
    private FeedbackResource(
            Feedback feedback,
            LearningResource learningResource,
            Integer displayOrder,
            String recommendationReason
    ) {
        this.feedback = feedback;
        this.learningResource = learningResource;
        this.displayOrder = displayOrder == null ? 1 : displayOrder;
        this.recommendationReason = recommendationReason;
    }
}
