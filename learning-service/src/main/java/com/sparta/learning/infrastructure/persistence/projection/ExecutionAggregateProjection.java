package com.sparta.learning.infrastructure.persistence.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 체결 전체를 SQL에서 1행으로 집계한 결과.
 *
 * 왜 인터페이스 프로젝션인가:
 * 엔티티로 받으면 1,000행이 영속성 컨텍스트에 올라가 커밋 시점 dirty checking 대상이 된다.
 * 프로젝션은 영속성 컨텍스트를 거치지 않으므로 그 비용이 0이다.
 *
 * 주의: 네이티브 쿼리의 컬럼 별칭은 반드시 큰따옴표로 감싼 camelCase여야 한다.
 *       snake_case 별칭은 인터페이스 프로젝션 매핑에서 조용히 null이 된다.
 */
public interface ExecutionAggregateProjection {

    long getTotalCount();
    long getBuyCount();
    long getSellCount();

    Long getTotalBuyQuantity();
    Long getTotalSellQuantity();

    BigDecimal getTotalBuyAmount();
    BigDecimal getTotalSellAmount();

    BigDecimal getHighestBuyPrice();
    BigDecimal getLowestBuyPrice();
    BigDecimal getHighestSellPrice();
    BigDecimal getLowestSellPrice();

    BigDecimal getRealizedProfit();

    OffsetDateTime getFirstExecutedAt();
    OffsetDateTime getLastExecutedAt();
}