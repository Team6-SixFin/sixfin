package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosedPositionSnapshotRepository extends JpaRepository<ClosedPositionSnapshot, Long> {
}
