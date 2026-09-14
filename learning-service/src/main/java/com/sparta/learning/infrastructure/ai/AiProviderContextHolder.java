package com.sparta.learning.infrastructure.ai;

/**
 * 현재 요청이 사용할 AI 제공자를 스레드 단위로 보관합니다.
 *
 * [Why ThreadLocal] 헤더는 Tomcat 스레드에서 읽히지만 실제 AI 호출은 @Async 워커에서 일어납니다.
 * 값을 GenerationContext 같은 응용 계층 객체에 실어 나르면 프로덕션 코드가
 * '테스트용 스위치'라는 개념을 알게 되어 오염됩니다.
 * 인프라 계층의 ThreadLocal + TaskDecorator 조합으로 응용/도메인 계층은 이를 전혀 모르게 둡니다.
 *
 * [주의] 스레드 풀은 스레드를 재사용하므로 반드시 finally 에서 clear() 해야 합니다.
 * 정리를 빠뜨리면 다음 요청이 이전 요청의 provider 를 물려받습니다.
 */
public final class AiProviderContextHolder {

    private static final ThreadLocal<String> CURRENT_PROVIDER = new ThreadLocal<>();

    private AiProviderContextHolder() {
    }

    public static void set(String provider) {
        CURRENT_PROVIDER.set(provider);
    }

    /** 오버라이드가 없으면 null. 이 경우 Router 가 기본 provider 를 사용합니다. */
    public static String get() {
        return CURRENT_PROVIDER.get();
    }

    public static void clear() {
        CURRENT_PROVIDER.remove();
    }
}