package com.sparta.trading.infrastructure.persistence.repository.outboxEvents;

import com.sparta.trading.domain.entity.OutboxEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventsJpaRepository extends JpaRepository<OutboxEvents, Long> {

    @Query("""
        SELECT o From OutboxEvents o
         WHERE (:status IS NULL OR o.status=:status)
           AND (:eventType IS NULL OR o.eventType=:eventType)
           AND (cast(:minRetryCount as string) IS NULL OR o.retryCount <= :minRetryCount)
           AND (cast(:from as string) IS NULL OR o.createdAt >= :from)
           AND (cast(:to as string) IS NULL OR o.createdAt <= :to)
           AND (:includePayload IS NOT TRUE OR o.payload IS NOT NULL)
    """)
    Page<OutboxEvents> searchOutBox(
            @Param("status") String status,
            @Param("eventType") String eventType,
            @Param("minRetryCount") Integer minRetryCount,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("includePayload") Boolean includePayload,
            Pageable pageable);

    @Query("SELECT COUNT(o) FROM OutboxEvents o WHERE o.status <> 'PUBLISHED'")
    long countUnpublished();

    @Query("SELECT o FROM OutboxEvents o WHERE o.status <> 'PUBLISHED'")
    List<OutboxEvents> findUnpublished(Pageable pageable);

    @Query("SELECT o.id FROM OutboxEvents o WHERE o.status = 'PENDING' ORDER BY o.occurredAt ASC limit :count")
    List<Long> findPendingIds(int count);
}
