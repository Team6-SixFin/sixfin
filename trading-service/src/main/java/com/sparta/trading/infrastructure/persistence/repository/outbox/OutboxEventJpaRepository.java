package com.sparta.trading.infrastructure.persistence.repository.outbox;

import com.sparta.trading.domain.entity.OutboxEvents;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvents, Long> {
}
