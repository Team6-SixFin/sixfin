package com.sparta.learning.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 피드백 처리 스레드풀 크기 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "learning.ai.executor")
public class AiExecutorProperties {

    // 항상 살아 있는 스레드 수. 여기까지는 큐를 거치지 않고 바로 실행된다
    private int corePoolSize = 5;

    // 큐가 가득 찬 뒤에야 여기까지 늘어난다. 늘어난 스레드는 유휴 60초 후 회수된다
    private int maxPoolSize = 20;

    // core 를 넘긴 작업이 쌓이는 대기열.
    // 이 값이 가득 차야 스레드가 core 이상으로 늘어나므로 max 보다 작게 잡는다.
    // 0 으로 두면 SynchronousQueue 가 되어 대기 없이 스레드 배정 아니면 거부다
    private int queueCapacity = 10;
}
