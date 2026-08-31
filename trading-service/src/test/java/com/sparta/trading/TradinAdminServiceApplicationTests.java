package com.sparta.trading;

import com.sparta.trading.application.dto.query.TradingSearchAccountsQuery;
import com.sparta.trading.application.service.TradingAdminQueryService;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.accounts.TradingAccountsQueryRepository;
import com.sparta.trading.presentation.dto.response.TradingAccountsResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TradinAdminServiceApplicationTests {

    @Mock
    private TradingAccountsQueryRepository adminQueryRepository;

    @InjectMocks
    private TradingAdminQueryService tradingAdminQueryService;

    @Test
    @DisplayName("계좌 목록 조회")
    void search_Success() {
    // given
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        TradingSearchAccountsQuery query = new TradingSearchAccountsQuery(userId, "createdAt", 0, 10);

        Accounts account = mock(Accounts.class);
        given(account.getId()).willReturn(accountId);
        given(account.getUserId()).willReturn(userId);
        given(account.getCashBalance()).willReturn(new BigDecimal("1000.0000"));
        given(account.getInitialDeposit()).willReturn(new BigDecimal("1000.0000"));
        given(account.getCurrency()).willReturn("USD");
        given(account.getCreatedAt()).willReturn(now);

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Accounts> accountsPage = new PageImpl<>(List.of(account), pageable, 1);

        given(adminQueryRepository.search(eq(userId), any(Pageable.class)))
                .willReturn(accountsPage);

        // when
        Page<TradingAccountsResponseDto> result = tradingAdminQueryService.search(query);

        // then - 콘솔 출력 확인
        for (TradingAccountsResponseDto dto : result.getContent()) {
            System.out.println("DTO 내용 확인: " + dto);
        }

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        // 레코드 필드 값 검증 (id, userId, currency)
        TradingAccountsResponseDto responseDto = result.getContent().get(0);
        assertThat(responseDto.id()).isEqualTo(accountId);
        assertThat(responseDto.userId()).isEqualTo(userId);
        assertThat(responseDto.currency()).isEqualTo("USD");

        verify(adminQueryRepository).search(eq(userId), any(Pageable.class));
    }

}
