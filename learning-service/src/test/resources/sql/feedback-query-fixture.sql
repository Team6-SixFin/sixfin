-- Learning 피드백 조회 API 로컬 테스트용 데이터
-- Hibernate가 테이블을 먼저 생성해야 하므로 learning-service를 한 번 실행한 뒤 사용한다.
--
-- X-User-Id : 11111111-1111-1111-1111-111111111111
-- positionId: 22222222-2222-2222-2222-222222222222
-- feedbackId: 920001(최초 매수), 920002(요청형/PENDING), 920003(종료 회고)

BEGIN;

-- 동일한 테스트 데이터를 반복해서 넣을 수 있도록 FK 자식 테이블부터 정리한다.
DELETE FROM feedback_resources
WHERE id IN (970001, 970002);

DELETE FROM feedback_diagnoses
WHERE id IN (950001, 950002, 950003, 950004, 950005, 950006, 950007, 950008, 950009, 950010);

DELETE FROM ai_requests
WHERE id IN (960001, 960002);

DELETE FROM learning_resources
WHERE id IN (940001, 940002);

DELETE FROM diagnosis_results
WHERE id IN (930001, 930002, 930003, 930004, 930005);

DELETE FROM feedbacks
WHERE id IN (920001, 920002, 920003);

DELETE FROM closed_position_snapshots
WHERE id = 912001;

DELETE FROM execution_snapshots
WHERE id IN (911001, 911002, 911003);

DELETE FROM consumed_events
WHERE id IN (910001, 910002, 910003, 910004);

-- Kafka 소비 이벤트 원본
INSERT INTO consumed_events (
    id, event_id, event_type, event_version, user_id,
    payload, occurred_at, consumed_at
) VALUES
(
    910001,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',
    'BUY_EXECUTED',
    1,
    '11111111-1111-1111-1111-111111111111',
    '{"executionId":"33333333-3333-4333-8333-333333333331","positionId":"22222222-2222-2222-2222-222222222222","stockCode":"AAPL"}'::jsonb,
    '2026-08-28T10:31:00+09:00',
    '2026-08-28T10:31:01+09:00'
),
(
    910002,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2',
    'BUY_EXECUTED',
    1,
    '11111111-1111-1111-1111-111111111111',
    '{"executionId":"33333333-3333-4333-8333-333333333332","positionId":"22222222-2222-2222-2222-222222222222","stockCode":"AAPL"}'::jsonb,
    '2026-08-29T10:00:00+09:00',
    '2026-08-29T10:00:01+09:00'
),
(
    910003,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3',
    'SELL_EXECUTED',
    1,
    '11111111-1111-1111-1111-111111111111',
    '{"executionId":"33333333-3333-4333-8333-333333333333","positionId":"22222222-2222-2222-2222-222222222222","stockCode":"AAPL"}'::jsonb,
    '2026-08-30T11:10:00+09:00',
    '2026-08-30T11:10:01+09:00'
),
(
    910004,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4',
    'POSITION_CLOSED',
    1,
    '11111111-1111-1111-1111-111111111111',
    '{"positionId":"22222222-2222-2222-2222-222222222222","stockCode":"AAPL"}'::jsonb,
    '2026-08-30T11:10:00+09:00',
    '2026-08-30T11:10:02+09:00'
);

-- 개별 체결 스냅샷: 최초 매수 → 추가 매수 → 전량 매도
INSERT INTO execution_snapshots (
    id, consumed_event_id, execution_id, order_id, position_id, user_id,
    stock_id, stock_symbol, stock_name, trade_type, is_new_position,
    quantity, executed_price, position_quantity_after, position_average_price,
    planned_stop_loss_price, investment_reason, recent_20d_high,
    recent_20d_low, recent_5d_return_rate, execution_realized_profit,
    quote_at, executed_at, created_at
) VALUES
(
    911001,
    910001,
    '33333333-3333-4333-8333-333333333331',
    '44444444-4444-4444-8444-444444444441',
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    1,
    'AAPL',
    'Apple Inc.',
    'BUY',
    true,
    10,
    180.0000,
    10,
    180.0000,
    171.0000,
    '실적 개선 기대',
    181.0000,
    165.0000,
    6.0000,
    NULL,
    '2026-08-28T10:30:59+09:00',
    '2026-08-28T10:31:00+09:00',
    '2026-08-28T10:31:01+09:00'
),
(
    911002,
    910002,
    '33333333-3333-4333-8333-333333333332',
    '44444444-4444-4444-8444-444444444442',
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    1,
    'AAPL',
    'Apple Inc.',
    'BUY',
    false,
    5,
    185.0000,
    15,
    181.6667,
    172.0000,
    '추가 상승 기대',
    185.0000,
    166.0000,
    8.0000,
    NULL,
    '2026-08-29T09:59:59+09:00',
    '2026-08-29T10:00:00+09:00',
    '2026-08-29T10:00:01+09:00'
),
(
    911003,
    910003,
    '33333333-3333-4333-8333-333333333333',
    '44444444-4444-4444-8444-444444444443',
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    1,
    'AAPL',
    'Apple Inc.',
    'SELL',
    false,
    15,
    170.0000,
    0,
    181.6667,
    172.0000,
    NULL,
    NULL,
    NULL,
    NULL,
    -175.0000,
    '2026-08-30T11:09:59+09:00',
    '2026-08-30T11:10:00+09:00',
    '2026-08-30T11:10:01+09:00'
);

-- 종료 포지션 최종 집계
INSERT INTO closed_position_snapshots (
    id, consumed_event_id, position_id, user_id, stock_id, stock_symbol,
    stock_name, total_bought_quantity, total_sold_quantity,
    average_entry_price, average_exit_price, planned_stop_loss_price,
    realized_profit, realized_return_rate, opened_at, closed_at, created_at
) VALUES (
    912001,
    910004,
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    1,
    'AAPL',
    'Apple Inc.',
    15,
    15,
    181.6667,
    170.0000,
    172.0000,
    -175.0000,
    -6.4220,
    '2026-08-28T10:31:00+09:00',
    '2026-08-30T11:10:00+09:00',
    '2026-08-30T11:10:02+09:00'
);

-- 최초 매수, 요청형, 종료 회고 피드백
INSERT INTO feedbacks (
    id, feedback_key, user_id, position_id, feedback_type,
    based_on_execution_id, status, content, ai_used, prompt_version,
    failure_reason, completed_at, created_at
) VALUES
(
    920001,
    'ENTRY:22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'ENTRY_FEEDBACK',
    NULL,
    'COMPLETED',
    '{
      "summary":"손절 계획은 설정했지만 최근 20일 최고가 부근에서 매수했습니다.",
      "overview":"단기 상승 이후 높은 가격 구간에서 진입한 거래입니다.",
      "strengths":["매수 전 손절가를 설정했습니다."],
      "improvements":["급등 직후 매수 여부를 한 번 더 확인해보세요."],
      "nextActions":["다음 매수 전 20일 가격 범위를 확인하세요."]
    }'::jsonb,
    true,
    'entry-v1',
    NULL,
    '2026-08-28T10:31:05+09:00',
    '2026-08-28T10:31:02+09:00'
),
(
    920002,
    'ON_DEMAND:22222222-2222-2222-2222-222222222222:33333333-3333-4333-8333-333333333332',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'ON_DEMAND_FEEDBACK',
    '33333333-3333-4333-8333-333333333332',
    'PENDING',
    NULL,
    false,
    NULL,
    NULL,
    NULL,
    '2026-08-29T10:00:02+09:00'
),
(
    920003,
    'POSITION_REVIEW:22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'POSITION_REVIEW',
    NULL,
    'COMPLETED',
    '{
      "summary":"계획 손절가보다 낮은 가격에서 포지션을 종료했습니다.",
      "overview":"추가 매수 후 평균 매수가가 올랐고 손절 계획이 늦게 실행됐습니다.",
      "strengths":["종료 후 거래 결과를 확인했습니다."],
      "improvements":["계획한 손절가에 도달했을 때 행동 기준을 명확히 해보세요."],
      "nextActions":["다음 거래에서는 매수 전 손절 주문 실행 계획도 기록하세요."],
      "reflectionQuestions":["손절가에 도달했을 때 매도를 미룬 이유는 무엇인가요?"]
    }'::jsonb,
    true,
    'position-review-v1',
    NULL,
    '2026-08-30T11:10:08+09:00',
    '2026-08-30T11:10:03+09:00'
);

-- 규칙 기반 진단 결과
INSERT INTO diagnosis_results (
    id, diagnosis_key, user_id, position_id, execution_snapshot_id,
    closed_position_snapshot_id, diagnosis_phase, rule_code, rule_version,
    result, metric_value, threshold_value, metrics, evidence, created_at
) VALUES
(
    930001,
    'ENTRY:33333333-3333-4333-8333-333333333331:STOP_LOSS_SET:v1',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    911001,
    NULL,
    'ENTRY',
    'STOP_LOSS_SET',
    1,
    'PASS',
    NULL,
    NULL,
    '{"stopLossSet":true,"plannedStopLossPrice":171.0000}'::jsonb,
    '{"message":"매수 전에 계획 손절가를 설정했습니다."}'::jsonb,
    '2026-08-28T10:31:02+09:00'
),
(
    930002,
    'ENTRY:33333333-3333-4333-8333-333333333331:HIGH_CHASING_BUY:v1',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    911001,
    NULL,
    'ENTRY',
    'HIGH_CHASING_BUY',
    1,
    'WARNING',
    99.4475,
    99.0000,
    '{"executedPrice":180.0000,"recent20DayHigh":181.0000,"highPriceRatio":99.4475}'::jsonb,
    '{"message":"매수가가 최근 20일 최고가의 99% 이상입니다."}'::jsonb,
    '2026-08-28T10:31:02+09:00'
),
(
    930003,
    'TRADE:33333333-3333-4333-8333-333333333332:REPEATED_HIGH_CHASING_BUY:v1',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    911002,
    NULL,
    'TRADE',
    'REPEATED_HIGH_CHASING_BUY',
    1,
    'WARNING',
    100.0000,
    99.0000,
    '{"executedPrice":185.0000,"recent20DayHigh":185.0000,"previousWarningCount":1}'::jsonb,
    '{"message":"추가 매수도 최근 고가 부근에서 실행했습니다."}'::jsonb,
    '2026-08-29T10:00:02+09:00'
),
(
    930004,
    'TRADE:33333333-3333-4333-8333-333333333333:SELL_BELOW_STOP_LOSS:v1',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    911003,
    NULL,
    'TRADE',
    'SELL_BELOW_STOP_LOSS',
    1,
    'VIOLATION',
    170.0000,
    172.0000,
    '{"executedPrice":170.0000,"plannedStopLossPrice":172.0000}'::jsonb,
    '{"message":"계획 손절가 172달러보다 낮은 170달러에 매도했습니다."}'::jsonb,
    '2026-08-30T11:10:02+09:00'
),
(
    930005,
    'CLOSE:22222222-2222-2222-2222-222222222222:STOP_LOSS_ADHERENCE:v1',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    NULL,
    912001,
    'CLOSE',
    'STOP_LOSS_ADHERENCE',
    1,
    'VIOLATION',
    1.0000,
    0.0000,
    '{"sellExecutionCount":1,"violationCount":1,"adherenceRate":0.0000}'::jsonb,
    '{"message":"매도 1번이 계획 손절가보다 낮게 체결됐습니다.","evidenceExecutionIds":["33333333-3333-4333-8333-333333333333"]}'::jsonb,
    '2026-08-30T11:10:03+09:00'
);

-- 각 피드백 작성에 사용한 진단 연결
INSERT INTO feedback_diagnoses (
    id, feedback_id, diagnosis_result_id, created_at
) VALUES
    (950001, 920001, 930001, '2026-08-28T10:31:03+09:00'),
    (950002, 920001, 930002, '2026-08-28T10:31:03+09:00'),
    (950003, 920002, 930001, '2026-08-29T10:00:03+09:00'),
    (950004, 920002, 930002, '2026-08-29T10:00:03+09:00'),
    (950005, 920002, 930003, '2026-08-29T10:00:03+09:00'),
    (950006, 920003, 930001, '2026-08-30T11:10:04+09:00'),
    (950007, 920003, 930002, '2026-08-30T11:10:04+09:00'),
    (950008, 920003, 930003, '2026-08-30T11:10:04+09:00'),
    (950009, 920003, 930004, '2026-08-30T11:10:04+09:00'),
    (950010, 920003, 930005, '2026-08-30T11:10:04+09:00');

-- AI 호출 이력: 조회 API 대상은 아니지만 실제 완료 데이터 형태를 맞춘다.
INSERT INTO ai_requests (
    id, feedback_id, request_id, model_name, prompt_version,
    input_json, output_json, status, error_message, completed_at, created_at
) VALUES
(
    960001,
    920001,
    '66666666-6666-4666-8666-666666666661',
    'fixture-model',
    'entry-v1',
    '{"feedbackType":"ENTRY_FEEDBACK","positionId":"22222222-2222-2222-2222-222222222222"}'::jsonb,
    '{"summary":"손절 계획은 설정했지만 고가 부근에서 매수했습니다."}'::jsonb,
    'SUCCESS',
    NULL,
    '2026-08-28T10:31:05+09:00',
    '2026-08-28T10:31:03+09:00'
),
(
    960002,
    920003,
    '66666666-6666-4666-8666-666666666662',
    'fixture-model',
    'position-review-v1',
    '{"feedbackType":"POSITION_REVIEW","positionId":"22222222-2222-2222-2222-222222222222"}'::jsonb,
    '{"summary":"계획 손절가보다 낮은 가격에서 포지션을 종료했습니다."}'::jsonb,
    'SUCCESS',
    NULL,
    '2026-08-30T11:10:08+09:00',
    '2026-08-30T11:10:05+09:00'
);

-- 상세 조회에서 확인할 활성 YouTube 학습 자료
INSERT INTO learning_resources (
    id, rule_code, search_query, provider, external_id, title,
    description, channel_id, channel_name, url, thumbnail_url,
    published_at, duration_seconds, view_count, status,
    searched_at, last_verified_at, expires_at
) VALUES
(
    940001,
    'HIGH_CHASING_BUY',
    '고점 추격 매수 투자 습관',
    'YOUTUBE',
    'fixture-video-high-chasing',
    '고점 추격 매수를 피하는 방법',
    '가격 위치를 확인하고 매수 계획을 세우는 방법을 설명합니다.',
    'fixture-channel-1',
    '투자습관연구소',
    'https://www.youtube.com/watch?v=fixture-high-chasing',
    'https://img.youtube.com/vi/fixture-high-chasing/0.jpg',
    '2026-01-10T09:00:00+09:00',
    600,
    12000,
    'ACTIVE',
    '2026-08-28T10:31:04+09:00',
    '2026-08-28T10:31:04+09:00',
    '2026-09-28T10:31:04+09:00'
),
(
    940002,
    'STOP_LOSS_ADHERENCE',
    '손절 원칙 준수 투자 습관',
    'YOUTUBE',
    'fixture-video-stop-loss',
    '손절 계획을 지키는 방법',
    '손절 기준을 미리 정하고 실행하는 방법을 설명합니다.',
    'fixture-channel-1',
    '투자습관연구소',
    'https://www.youtube.com/watch?v=fixture-stop-loss',
    'https://img.youtube.com/vi/fixture-stop-loss/0.jpg',
    '2026-01-11T09:00:00+09:00',
    720,
    15000,
    'ACTIVE',
    '2026-08-30T11:10:06+09:00',
    '2026-08-30T11:10:06+09:00',
    '2026-09-30T11:10:06+09:00'
);

INSERT INTO feedback_resources (
    id, feedback_id, learning_resource_id, display_order,
    recommendation_reason, created_at
) VALUES
(
    970001,
    920001,
    940001,
    1,
    '고점 추격 매수 경고가 발생해 추천합니다.',
    '2026-08-28T10:31:05+09:00'
),
(
    970002,
    920003,
    940002,
    1,
    '손절 원칙 위반 진단이 발생해 추천합니다.',
    '2026-08-30T11:10:07+09:00'
);

COMMIT;

-- 적재 확인용 조회
SELECT id, feedback_type, status, position_id, created_at
FROM feedbacks
WHERE id IN (920001, 920002, 920003)
ORDER BY created_at;
