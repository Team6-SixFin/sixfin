package com.sparta.trading.global.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@MappedSuperclass
public class BaseEntity extends AuditableEntity {

    private Instant deletedAt;

    private UUID deletedBy;

    public void markDeleted(UUID deletedBy){
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
