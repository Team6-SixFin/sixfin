package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExecutionSnapshotRepository extends JpaRepository<ExecutionSnapshot, Long> {

    List<ExecutionSnapshot> findByPositionIdInOrderByExecutedAtAsc(Collection<UUID> positionIds);
}
