package com.sparta.trading.infrastructure.persistence.repository.order;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

 interface TradingOrderJpaRepository extends JpaRepository<Orders, UUID> {

     @Query("SELECT o FROM Orders o")
     Page<Orders> searchOrder(Pageable pageable);
 }
