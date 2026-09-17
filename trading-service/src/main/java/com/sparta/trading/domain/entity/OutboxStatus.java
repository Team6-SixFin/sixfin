package com.sparta.trading.domain.entity;

import lombok.AllArgsConstructor;

import java.util.Set;

public enum OutboxStatus {
    PUBLISHED,
    FAILED,
    PENDING,
    ;

    public boolean validateNext(OutboxStatus next) {
        return switch (this) {
            case PENDING, FAILED -> next == PUBLISHED || next == FAILED;
            case PUBLISHED -> false;
        };
    }
}
