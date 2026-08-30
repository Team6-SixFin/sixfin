package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, Long> {

    boolean existsByEventId(UUID eventId);
}
