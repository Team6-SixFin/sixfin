package com.sparta.trading.infrastructure.messaging.kafka.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.trading.application.dto.event.BuyExecutedPayload;
import com.sparta.trading.application.dto.event.MarketContextPayload;
import com.sparta.trading.application.dto.event.TradingEventEnvelope;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.infrastructure.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KafkaConfig가 실제로 브로커에 붙어 JsonSerializer로 직렬화까지 해내는지 확인한다.
 * payload는 손으로 만든 JSON이 아니라 실제 OutboxEvents/TradingEventEnvelope를 그대로 써서,
 * BigDecimal·OffsetDateTime·중첩 marketContext까지 프로덕션과 동일한 모양으로 검증한다.
 */
@Slf4j
@SpringBootTest(classes = {KafkaConfig.class, TradingKafkaProducer.class})
@EmbeddedKafka(partitions = 1, topics = "trade-events.v1")
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "logging.level.kafka=WARN",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.state.change.logger=WARN"
})
class TradingKafkaProducerIntegrationTest {

    private static final String TOPIC = "trade-events.v1";

    @Autowired
    private TradingKafkaProducer producer;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("outbox-publisher-test", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void sendsMessageThatDeserializesToOriginalEnvelopeShape() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        TradingEventEnvelope envelope = new TradingEventEnvelope(
                eventId,
                "BUY_EXECUTED",
                1,
                OffsetDateTime.now(),
                userId,
                new BuyExecutedPayload(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true,
                        1L, "AAPL", "Apple", 10,
                        BigDecimal.valueOf(183.17), 10, BigDecimal.valueOf(183.17),
                        BigDecimal.valueOf(170), "실적 개선 기대",
                        new MarketContextPayload(
                                BigDecimal.valueOf(190), BigDecimal.valueOf(160),
                                BigDecimal.valueOf(7.2), OffsetDateTime.now()),
                        OffsetDateTime.now()
                )
        );
        // 실제 발행 코드(TradingKafkaOutboxPublisher)와 동일하게, OutboxEvents 엔티티에서 payload/partitionKey를 꺼내 보낸다
        OutboxEvents outboxEvent = OutboxEvents.buyExecuted(
                eventId, UUID.randomUUID(), userId, objectMapper.valueToTree(envelope), Instant.now());

        producer.sendSync(TOPIC, outboxEvent.getPartitionKey(), outboxEvent.getPayload(), 5);

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(5));
        log.info("record: [{}]", record);
        assertThat(record.key()).isEqualTo(userId.toString());

        JsonNode received = objectMapper.readTree(record.value());
        assertThat(received.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(received.get("eventType").asText()).isEqualTo("BUY_EXECUTED");
        assertThat(received.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(received.get("payload").get("stockCode").asText()).isEqualTo("AAPL");
        assertThat(received.get("payload").get("marketContext").get("recent20DayHigh").asDouble()).isEqualTo(190.0);
    }
}
