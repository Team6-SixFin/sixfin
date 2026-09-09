package com.sparta.learning.infrastructure.config;

import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* 재시도 정책과 DLT 발행 동작을 검증  */
class KafkaErrorHandlerConfigTest {

    private static final String TOPIC = "trade-events.v1";

    private KafkaOperations<String, TradingEventEnvelope> kafkaOperations;
    private KafkaOperations<String, byte[]> rawKafkaOperations;
    private SimpleMeterRegistry meterRegistry;
    private DefaultErrorHandler errorHandler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaOperations = mock(KafkaOperations.class);
        rawKafkaOperations = mock(KafkaOperations.class);

        // recoverer가 발행 완료를 기다리므로 이미 완료된 future를 돌려준다
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(rawKafkaOperations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        meterRegistry = new SimpleMeterRegistry();
        errorHandler = new KafkaErrorHandlerConfig().kafkaErrorHandler(
                kafkaOperations, rawKafkaOperations, new LearningMetrics(meterRegistry));
    }


    // removeClassification은 등록된 분류를 지우고 그 값(재시도 여부)을 돌려준다
    @Test
    void 계약_위반_예외는_재시도하지_않는다() {
        assertThat(errorHandler.removeClassification(InvalidTradeEventException.class)).isFalse();
    }

    @Test
    void 일시적_예외는_재시도_제외_대상이_아니다() {
        assertThat(errorHandler.removeClassification(IllegalStateException.class)).isNull();
    }

    // 기본값은 간격이 0이라 일시 장애를 넘기지 못한다. 간격을 늘려 복구할 시간을 준다
    @Test
    void 재시도_간격이_점점_늘어난다() {
        BackOffExecution execution = backOff().start();

        long first = execution.nextBackOff();
        long second = execution.nextBackOff();

        assertThat(first).isEqualTo(1_000L);
        assertThat(second).isGreaterThan(first);
    }

    // 상한이 없으면 무한 재시도가 되어 DLT로 가지 않고 파티션 전체가 멈춘다
    @Test
    void 재시도는_상한에_도달하면_중단된다() {
        BackOffExecution execution = backOff().start();

        long interval = 0L;
        for (int i = 0; i < 100 && interval != BackOffExecution.STOP; i++) {
            interval = execution.nextBackOff();
            assertThat(interval).isLessThanOrEqualTo(10_000L);
        }

        assertThat(interval).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void 복구_시점에_DLT_토픽으로_발행한다() {
        ArgumentCaptor<ProducerRecord<String, TradingEventEnvelope>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        recoverer().accept(record(), new IllegalStateException("진단 실패"));

        verify(kafkaOperations).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo(TOPIC + "-dlt");
    }

    // DLT 발행은 진단이나 수집이 누락됐다는 뜻이므로 알림을 걸 수 있도록 메트릭을 남긴다
    @Test
    void DLT_발행을_메트릭으로_남긴다() {
        recoverer().accept(record(), new IllegalStateException("진단 또는 수집 실패"));

        assertThat(meterRegistry.get("learning.trade.events.dead.letter")
                .tag("exception", "IllegalStateException")
                .counter()
                .count()).isEqualTo(1.0);
    }

    // 역직렬화 실패처럼 본문이 없는 레코드에서도 발행과 계측이 중단되면 안된다
    @Test
    void 본문이_없어도_DLT_발행과_계측이_동작한다() {
        ConsumerRecord<String, TradingEventEnvelope> record =
                new ConsumerRecord<>(TOPIC, 0, 0L, "key", null);

        recoverer().accept(record, new InvalidTradeEventException("역직렬화 실패"));

        // 값이 없으면 첫 템플릿(byte[])이 쓰인다
        verify(rawKafkaOperations).send(any(ProducerRecord.class));
        assertThat(meterRegistry.get("learning.trade.events.dead.letter")
                .tag("event_type", "UNKNOWN")
                .counter()
                .count()).isEqualTo(1.0);
    }

    private ConsumerRecordRecoverer recoverer() {
        return new KafkaErrorHandlerConfig().deadLetterRecoverer(
                kafkaOperations, rawKafkaOperations, new LearningMetrics(meterRegistry));
    }

    private BackOff backOff() {
        return new KafkaErrorHandlerConfig().backOff();
    }

    private ConsumerRecord<String, TradingEventEnvelope> record() {
        return new ConsumerRecord<>(TOPIC, 0, 0L, "key", mock(TradingEventEnvelope.class));
    }
}
