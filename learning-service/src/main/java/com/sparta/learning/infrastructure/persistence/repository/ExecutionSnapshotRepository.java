package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionSnapshotRepository extends JpaRepository<ExecutionSnapshot, Long> {
}
