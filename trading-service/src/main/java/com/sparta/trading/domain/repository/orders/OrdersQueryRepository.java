package com.sparta.trading.domain.repository.orders;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.domain.entity.Orders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrdersQueryRepository {
    Page<Orders> searchOrder(
            TradingAdminSearchOrderQuery query,
            Long stockId,
            List<UUID> accountIds,
            Pageable pageable);

    Slice<Orders> searchOrderSlice(
            TradingAdminSearchOrderQuery query,
            Long stockId,
            List<UUID> accountIds,
            Pageable pageable);

    Optional<Orders> findByAccountIdAndRequestId(UUID accountId, UUID requestId);

    Optional<Orders> findById(UUID id);

    List<DuplicateRequestGroup> findDuplicateRequestGroups(UUID accountId);

}
