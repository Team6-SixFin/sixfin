package com.sparta.trading.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    /** 웹 요청 밖에서 저장해도 명세의 NOT NULL 감사 주체를 보존하기 위한 보조 값이다. */
    protected void initializeAudit(UUID actorId) {
        UUID actor = Objects.requireNonNull(actorId, "actorId must not be null");
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    protected void markUpdatedBy(UUID actorId) {
        this.updatedBy = Objects.requireNonNull(actorId, "actorId must not be null");
    }
}
