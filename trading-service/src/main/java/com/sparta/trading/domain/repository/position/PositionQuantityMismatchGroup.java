package com.sparta.trading.domain.repository.position;

import java.util.UUID;

public interface PositionQuantityMismatchGroup {
    UUID getPositionId();
    UUID getAccountId();
    Integer getPositionQuantity();
    Long getExecutionNetQuantity();
}
