package com.sparta.trading.domain.repository.orders;

import java.util.UUID;

/** 동일 계좌에서 같은 request_id로 2건 이상 존재하는 그룹 하나. */
public interface DuplicateRequestGroup {
    UUID getAccountId();
    UUID getRequestId();
    Long getDuplicateCount();
}
