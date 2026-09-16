package com.sparta.learning.application.dto.result;

import java.util.UUID;

/** 포지션 하나의 종목 정보. 그 포지션의 최초 체결 스냅샷에서 가져옴 */
public interface PositionStockInfo {

    UUID getPositionId();

    String getStockSymbol();

    String getStockName();
}
