package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionSnapshotRepository extends JpaRepository<ExecutionSnapshot, Long> {

    List<ExecutionSnapshot> findByPositionIdInOrderByExecutedAtAsc(Collection<UUID> positionIds);

    Optional<ExecutionSnapshot> findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(UUID positionId, UUID userId);

    Optional<ExecutionSnapshot> findByConsumedEventEventId(UUID eventId);
    // 요청형 피드백을 위한 최신(마지막) 체결 조회용
    Optional<ExecutionSnapshot> findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(UUID positionId, UUID userId);
}
