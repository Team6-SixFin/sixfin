package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClosedPositionSnapshotRepository extends JpaRepository<ClosedPositionSnapshot, Long> {

    // 중복 이벤트에서 기존 종료 스냅샷을 찾아 CLOSE 진단을 다시 실행하기 위해 사용
    Optional<ClosedPositionSnapshot> findByConsumedEventEventId(UUID eventId);

    // Ai피드백 생성을 위한 종료 포지션 스냅샷 조회
    Optional<ClosedPositionSnapshot> findByPositionId(UUID positionId);
}
