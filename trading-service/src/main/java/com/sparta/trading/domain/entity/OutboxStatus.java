package com.sparta.trading.domain.entity;

public enum OutboxStatus {
    PUBLISHED,
    FAILED,
    PENDING,
    RETRYING
    ;

    public boolean validateNext(OutboxStatus next) {
        return switch (this) {
            case PENDING -> next == PUBLISHED || next == FAILED;
            case FAILED -> next == RETRYING;
            case RETRYING -> next == FAILED || next == PUBLISHED;
            case PUBLISHED -> false;
        };
    }
}
