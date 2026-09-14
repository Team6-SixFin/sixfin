package com.sparta.learning.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 성능 측정용 Stub AI 설정 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "learning.ai.stub")
public class StubAiProperties {

    /**
     * Stub 어댑터를 '빈으로 등록할지' 여부. 기본 false.
     *
     * true 는 Stub 구현체를 등록한다는 뜻입니다.
     * 헤더가 없는 요청과 Kafka 처리의 기본 제공자는 learning.ai.provider 로 결정하고,
     * HTTP 요청은 인증된 헤더를 통해 Stub 으로 전환할 수 있습니다.
     */
    private boolean enabled = false;

    /**
     * 헤더 오버라이드를 허용하는 토큰. 비어 있으면 오버라이드가 동작하지 않습니다.
     *
     * [Why] learning-service 는 서비스 포트가 외부에 열려 있을 수 있고,
     * 게이트웨이를 거쳤는지 검증하는 장치가 없습니다.
     * 토큰 없이 헤더만으로 전환되면 "모든 AI 응답을 가짜로 바꾸는" 스위치가 공개됩니다.
     */
    private String overrideToken = "";

    /** 고정 응답 전 대기 시간. 성능측정 명세 6번 기준값은 2초. */
    private Duration latency = Duration.ofSeconds(2);

    /** 지연에 더할 랜덤 편차(±). 0이면 완전 균일 = 재현성 우선. */
    private Duration jitter = Duration.ZERO;

    /** 의도적 실패 비율(0.0~1.0). "성공·실패 수" 지표 검증 시 0.05 정도로 사용. */
    private double failureRate = 0.0;

    /** true면 latency를 다 기다린 뒤 실패(= 스레드 오래 점유), false면 즉시 실패. */
    private boolean failAfterLatency = true;
}
