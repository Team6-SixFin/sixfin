package com.sparta.trading.domain.repository.positions;

import java.util.UUID;

public interface PositionsQuantityMismatchGroup {
    UUID getPositionId();
    UUID getAccountId();
    Integer getPositionQuantity();
    Long getExecutionNetQuantity();
}
