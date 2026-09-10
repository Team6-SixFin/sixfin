package com.sparta.trading.infrastructure.persistence.repository.executions;

import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.repository.executions.ExecutionsCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExecutionsCommandRepositoryImpl implements ExecutionsCommandRepository {

    private final ExecutionsJpaRepository executionJpaRepository;

    @Override
    public Executions save(Executions execution) {
        return executionJpaRepository.save(execution);
    }

}
