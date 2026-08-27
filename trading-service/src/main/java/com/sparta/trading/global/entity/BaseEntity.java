package com.sparta.trading.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column(updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    private LocalDateTime updatedAt;

    private UUID updatedBy;

    private LocalDateTime deletedAt;

    private UUID deletedBy;

    public void markDeleted(UUID deletedBy){
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
