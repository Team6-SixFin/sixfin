package com.sparta.learning.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 실패한 피드백 재처리 정책 값 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "learning.feedback.retry")
public class FeedbackRetryProperties {

    //재처리 스케줄러 동작 여부. 측정이나 장애 대응 중에 끌 수 있게 둔다
    private boolean enabled = true;

    // 한 주기에 다시 시도할 최대 건수. executor 를 재포화시키지 않는 것이 기준
    private int batchSize = 5;

    // 시도 횟수 상한. 넘으면 재처리 대상에서 제외되고 관리자 확인 대상이 된다.
    private int maxAttempts = 20;

    // 이 시간이 지나도록 PROCESSING 에 머문 피드백은 점유가 끊긴 것으로 보고 회수한다
    private Duration staleProcessingAfter = Duration.ofMinutes(10);
}
