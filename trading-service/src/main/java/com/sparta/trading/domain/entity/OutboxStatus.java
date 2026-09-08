package com.sparta.trading.domain.entity;

import lombok.AllArgsConstructor;

import java.util.Set;

@AllArgsConstructor
public enum OutboxStatus {
    PUBLISHED(Set.of()),
    FAILED(Set.of(PUBLISHED)),
    PENDING(Set.of(PUBLISHED, FAILED)),
    ;

    private final Set<OutboxStatus> next;
    public boolean validateNext(OutboxStatus next){
        return this.next.contains(next);
    }
}
