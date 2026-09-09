package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.FailedEvent;
import com.sparta.learning.domain.model.FailedEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface FailedEventRepository
        extends JpaRepository<FailedEvent, Long>, FailedEventRepositoryCustom {

    /**
     * PENDING인 경우에만 PROCESSING으로 바꾼다.
     * 조건부 UPDATE 한 문장이라 동시에 들어온 요청 중 하나만 1을 돌려받는다.
     * 애플리케이션에서 조회 후 변경하면 그 사이에 다른 요청이 끼어들 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FailedEvent failedEvent
               set failedEvent.status = :processing,
                   failedEvent.lastRetriedAt = :now
             where failedEvent.id = :id
               and failedEvent.status = :pending
            """)
    int claimForRetry(
            @Param("id") Long id,
            @Param("pending") FailedEventStatus pending,
            @Param("processing") FailedEventStatus processing,
            @Param("now") OffsetDateTime now
    );
}
