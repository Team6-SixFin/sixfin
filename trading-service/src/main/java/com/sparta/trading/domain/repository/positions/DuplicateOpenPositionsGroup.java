package com.sparta.trading.domain.repository.positions;

import java.util.UUID;

/** 동일 계좌·종목에 OPEN 포지션이 2건 이상 존재하는 그룹 하나. */
public interface DuplicateOpenPositionGroup {
    UUID getAccountId();
    Long getStockId();
    Long getDuplicateCount();
}
