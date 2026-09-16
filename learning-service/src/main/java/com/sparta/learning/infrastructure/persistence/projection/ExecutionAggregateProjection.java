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

    /**
     * [주의] OffsetDateTime이 아니라 String이다.
     *
     * 네이티브 쿼리는 엔티티 메타데이터가 없어 Hibernate가 결과 컬럼의 Java 타입을 알 수 없다.
     * MIN(executed_at)/MAX(executed_at)이 JDBC 드라이버 기본 매핑인 java.time.Instant 로 올라오는데,
     * Instant는 오프셋 정보가 없어 OffsetDateTime으로 자동 변환되지 않고
     * Spring Data 프로젝션 프록시가 UnsupportedOperationException을 던진다.
     *
     * 드라이버·Hibernate 버전에 따라 올라오는 타입이 달라질 수 있으므로,
     * SQL의 to_char로 ISO-8601 문자열을 확정해 받는다.
     * DTO의 firstExecutedAt / lastExecutedAt 도 String이라 중간 변환이 아예 없어진다.
     *
     * (엔티티 ExecutionSnapshot.executedAt은 그대로 OffsetDateTime이며 팀 컨벤션을 따른다.
     *  여기는 도메인 타입이 아니라 DB 읽기 전용 구조체다.)
     */
    String getFirstExecutedAt();
    String getLastExecutedAt();
}