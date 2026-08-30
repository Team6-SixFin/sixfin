package com.sparta.learning.domain.entity;

import com.sparta.learning.global.entity.BaseEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "feedback_diagnoses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_feedback_diagnosis",
                columnNames = {"feedback_id", "diagnosis_result_id"}
        ),
        indexes = {
                @Index(name = "idx_feedback_diagnosis_feedback", columnList = "feedback_id"),
                @Index(name = "idx_feedback_diagnosis_result", columnList = "diagnosis_result_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackDiagnosis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnosis_result_id", nullable = false)
    private DiagnosisResult diagnosisResult;

    public FeedbackDiagnosis(Feedback feedback, DiagnosisResult diagnosisResult) {
        this.feedback = feedback;
        this.diagnosisResult = diagnosisResult;
    }
}
