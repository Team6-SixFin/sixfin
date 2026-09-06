package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClosedPositionSnapshotRepository extends JpaRepository<ClosedPositionSnapshot, Long> {

    // Ai피드백 생성을 위한 종료 포지션 스냅샷 조회
    Optional<ClosedPositionSnapshot> findByPositionId(UUID positionId);

}
