package com.sparta.trading.infrastructure.loader;

import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.infrastructure.persistence.repository.candles.DailyCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Yahoo Finance 수집 스크립트(tools/market-data-collector)가 만든 CSV를 읽어 DB에 적재한다.
 * stocks/daily_candles/price_candles 각각 이미 데이터가 있으면 그 테이블만 건너뛴다(멱등) —
 * 재기동 중단 등으로 일부만 적재된 상태에서 다시 떠도 안전하게 이어서 채운다.
 * 대량 캔들 데이터는 IDENTITY PK의 JPA 배치 insert 제약을 피하기 위해 JdbcTemplate으로 직접 적재한다.
 * stocks은 우선 20 종목 뿐이라 JPA 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class MarketDataCsvLoader implements ApplicationRunner {

    private static final int BATCH_SIZE = 500;

    private final StocksRepository stocksRepository;
    private final DailyCandlesRepository dailyCandlesRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MarketDataCsvProperties properties;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path csvDir = Path.of(properties.csvDir());

        Map<String, Long> stockIdBySymbol = loadStocksIfMissing(csvDir.resolve("stocks.csv"));
        long dailyCount = loadDailyCandlesIfMissing(csvDir.resolve("daily_candles.csv"), stockIdBySymbol);
        long priceCount = loadPriceCandlesIfMissing(csvDir.resolve("price_candles.csv"), stockIdBySymbol);

        log.info("[market-data-csv-loader] 적재 확인 완료 - 종목 {}건, 일봉 {}건, 분봉 {}건",
                stockIdBySymbol.size(), dailyCount, priceCount);
    }

    private Map<String, Long> loadStocksIfMissing(Path path) throws IOException {
        if (stocksRepository.count() > 0) {
            log.info("[market-data-csv-loader] 이미 종목 데이터가 존재하여 건너뜁니다.");
            return stocksRepository.findAll().stream()
                    .collect(Collectors.toMap(Stocks::getSymbol, Stocks::getId));
        }
        return loadStocks(path);
    }

    private long loadDailyCandlesIfMissing(Path path, Map<String, Long> stockIdBySymbol) throws IOException {
        if (dailyCandlesRepository.count() > 0) {
            log.info("[market-data-csv-loader] 이미 일봉 데이터가 존재하여 건너뜁니다.");
            return dailyCandlesRepository.count();
        }
        return loadDailyCandles(path, stockIdBySymbol);
    }

    private long loadPriceCandlesIfMissing(Path path, Map<String, Long> stockIdBySymbol) throws IOException {
        if (priceCandlesRepository.count() > 0) {
            log.info("[market-data-csv-loader] 이미 분봉 데이터가 존재하여 건너뜁니다.");
            return priceCandlesRepository.count();
        }
        return loadPriceCandles(path, stockIdBySymbol);
    }

    private Map<String, Long> loadStocks(Path path) throws IOException {
        List<Stocks> stocks = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            reader.readLine(); // header: symbol,name,market,currency,is_active
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] c = line.split(",", -1);
                stocks.add(Stocks.builder()
                        .symbol(c[0])
                        .name(c[1])
                        .market(c[2])
                        .currency(c[3])
                        .active(Boolean.parseBoolean(c[4]))
                        .build());
            }
        }

        List<Stocks> saved = stocksRepository.saveAll(stocks);
        Map<String, Long> stockIdBySymbol = new HashMap<>();
        for (Stocks stock : saved) {
            stockIdBySymbol.put(stock.getSymbol(), stock.getId());
        }
        return stockIdBySymbol;
    }

    private long loadDailyCandles(Path path, Map<String, Long> stockIdBySymbol) throws IOException {
        String sql = """
                INSERT INTO p_daily_candles
                (stock_id, trade_date, open_price, high_price, low_price, close_price, volume,
                 recent_20d_high, recent_20d_low, recent_5d_return, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """;

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            // 헤더 한 줄 스킵: symbol,trade_date,open,high,low,close,volume,recent_20d_high,recent_20d_low,recent_5d_return
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] c = line.split(",", -1);
                Long stockId = stockIdBySymbol.get(c[0]);
                if (stockId == null) {
                    log.warn("[market-data-csv-loader] daily_candles.csv 의 종목 {} 이(가) stocks.csv 에 없어 건너뜁니다.", c[0]);
                    continue;
                }
                batch.add(new Object[]{
                        stockId,
                        LocalDate.parse(c[1]),
                        new BigDecimal(c[2]), new BigDecimal(c[3]), new BigDecimal(c[4]), new BigDecimal(c[5]),
                        Long.parseLong(c[6]),
                        parseNullableDecimal(c[7]), parseNullableDecimal(c[8]), parseNullableDecimal(c[9]),
                });
                total++;
                if (batch.size() == BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(sql, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        return total;
    }

    private long loadPriceCandles(Path path, Map<String, Long> stockIdBySymbol) throws IOException {
        String sql = """
                INSERT INTO p_price_candles
                (stock_id, seq, market_time, open_price, high_price, low_price, close_price, volume,
                 recent_20d_high, recent_20d_low, recent_5d_return, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """;

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            // 헤더 한 줄 스킵
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] c = line.split(",", -1);
                Long stockId = stockIdBySymbol.get(c[0]);
                if (stockId == null) {
                    log.warn("[market-data-csv-loader] price_candles.csv 의 종목 {} 이(가) stocks.csv 에 없어 건너뜁니다.", c[0]);
                    continue;
                }
                batch.add(new Object[]{
                        stockId,
                        Long.parseLong(c[1]),
                        Timestamp.from(Instant.parse(c[2])),
                        new BigDecimal(c[3]), new BigDecimal(c[4]), new BigDecimal(c[5]), new BigDecimal(c[6]),
                        Long.parseLong(c[7]),
                        parseNullableDecimal(c[8]), parseNullableDecimal(c[9]), parseNullableDecimal(c[10]),
                });
                total++;
                if (batch.size() == BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(sql, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        return total;
    }

    private BigDecimal parseNullableDecimal(String value) {
        return value.isBlank() ? null : new BigDecimal(value);
    }
}
