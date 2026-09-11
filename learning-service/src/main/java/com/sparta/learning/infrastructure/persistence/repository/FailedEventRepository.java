package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedEventRepository
        extends JpaRepository<FailedEvent, Long>, FailedEventRepositoryCustom {
}
