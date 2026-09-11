package com.sparta.trading.domain.repository.executions;

import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionsCommandRepository {

    Executions save(Executions execution);

}
