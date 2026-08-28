package com.sparta.learning.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "diagnosis_results",
        indexes = {
                @Index(name = "idx_diagnosis_result_user_id", columnList = "user_id"),
                @Index(name = "idx_diagnosis_result_rule_code", columnList = "rule_code"),
                @Index(name = "idx_diagnosis_result_result", columnList = "result")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiagnosisResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diagnosis_key", nullable = false, unique = true, length = 150)
    private String diagnosisKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_snapshot_id")
    private ExecutionSnapshot executionSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_position_snapshot_id")
    private ClosedPositionSnapshot closedPositionSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagnosis_phase", nullable = false, length = 20)
    private DiagnosisPhase diagnosisPhase;

    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private DiagnosisStatus result;

    @Column(name = "metric_value", precision = 19, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "threshold_value", precision = 19, scale = 4)
    private BigDecimal thresholdValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", nullable = false, columnDefinition = "jsonb")
    private JsonNode metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, columnDefinition = "jsonb")
    private JsonNode evidence;

    @Builder
    private DiagnosisResult(
            String diagnosisKey,
            UUID userId,
            UUID positionId,
            ExecutionSnapshot executionSnapshot,
            ClosedPositionSnapshot closedPositionSnapshot,
            DiagnosisPhase diagnosisPhase,
            String ruleCode,
            int ruleVersion,
            DiagnosisStatus result,
            BigDecimal metricValue,
            BigDecimal thresholdValue,
            JsonNode metrics,
            JsonNode evidence
    ) {
        this.diagnosisKey = diagnosisKey;
        this.userId = userId;
        this.positionId = positionId;
        this.executionSnapshot = executionSnapshot;
        this.closedPositionSnapshot = closedPositionSnapshot;
        this.diagnosisPhase = diagnosisPhase;
        this.ruleCode = ruleCode;
        this.ruleVersion = ruleVersion < 1 ? 1 : ruleVersion;
        this.result = result;
        this.metricValue = metricValue;
        this.thresholdValue = thresholdValue;
        this.metrics = metrics == null ? JsonNodeFactory.instance.objectNode() : metrics;
        this.evidence = evidence == null ? JsonNodeFactory.instance.objectNode() : evidence;
    }
}
