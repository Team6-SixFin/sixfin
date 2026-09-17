package com.sparta.learning.infrastructure.ai;

// AI 프롬프트 관리
public class PromptTemplate {

    /**
     * [신규] 요약 컨텍스트 해석 규칙.
     *
     * 왜 필요한가:
     * 1,000건 중 23건만 보내면서 아무 안내가 없으면 AI는 "총 23회 매매하셨네요"라고 쓴다.
     * 토큰은 줄었는데 사용자에게 보이는 사실이 틀리면 개선이 아니라 퇴행이다.
     */
    private static final String CONTEXT_GUIDE =
            """

            [입력 데이터 해석 규칙]
            - contextScope.truncated가 true면 executions는 전체가 아니라 대표 체결만 담은 요약본이다.
            - 매매 횟수를 언급할 때는 executions 배열 길이가 아니라 contextScope.totalExecutionCount를 사용한다.
            - executions[].role: FIRST(최초 진입) / LARGEST_BUY(최대 매수) / LARGEST_SELL(최대 매도) / RECENT(최근 체결)
            - executions는 executedAt 오름차순이다. 배열 위치는 전체에서의 순번이 아니라 요약본 안의 순서일 뿐이다.
            - executions[].positionAveragePrice는 그 체결 직후의 포지션 평균 매수가다.
              추가 매수로 평균 단가가 어떻게 변했는지 설명할 때 이 값을 사용한다.
            - 전체 통계는 executionSummary를 사용한다. executions를 직접 합산하지 않는다.
            - closedInfo가 있으면 전체 매수·매도 수량, 평균가, 실현손익은 closedInfo가 기준이다.
              executionSummary에는 건수와 개별 체결가의 최대·최소만 들어 있다.
            - diagnosisSummary는 규칙별 진단 전체 집계다. occurrenceCount가 2 이상이면 그 습관이 반복됐다는 뜻이며,
              "반복되는 매매 습관"을 지적할 때 이 값을 근거로 삼는다.
            - diagnoses는 각 규칙·결과 그룹의 대표 진단 1건이다.
              metricValue / thresholdValue / metrics는 모두 그 대표 1건의 값이고,
              그룹 전체의 최대·최소는 diagnosisSummary.maxMetricValue / minMetricValue에 있다.
            - diagnoses[].metrics에는 계산에 쓰인 세부 수치가 들어 있다.
              예: recent20dHigh(20일 최고가), plannedStopLossPrice(계획 손절가),
                  minWidthRate·maxWidthRate(적정 손절 폭 범위), chasedBefore(이전 고점 추격 여부).
              구체적인 숫자를 인용할 때 이 값을 사용한다.
            - metricValue가 null인 진단은 metrics 안의 수치를 근거로 삼는다.
            - evidenceMessage를 근거 문장으로 인용한다.
            - 데이터에 없는 사실을 추측해서 쓰지 않는다.
            """;

    // 시스템 프롬프트: JSON 강제
    public static final String SYSTEM_PROMPT =
            """
            너는 주식 트레이딩 전문가이자 멘토야. 사용자의 매매 기록과 규칙 기반 진단 결과를 바탕으로 객관적이고 유용한 피드백을 제공해. \
            반드시 아래의 순수 JSON 형식으로만 응답해. 마크다운이나 추가 설명은 절대 포함하지 마.
            {
              "summary": "최상위 한 문장 요약 (목록 API용)",
              "overview": "전체 상황 설명 및 총평",
              "strengths": ["잘한 점1", "잘한 점2"],
              "improvements": ["개선할 점1"],
              "nextActions": ["다음 행동 제안1"],
              "reflectionQuestions": ["회고 질문1"]
            }""" + CONTEXT_GUIDE;

    // 1. 요청형 매매 피드백 (중간 점검)
    public static final String ON_DEMAND_PROMPT =
            """
            사용자가 현재 보유 중인 포지션에 대해 중간 점검(ON_DEMAND) 피드백을 요청했습니다. \
            진입 이후의 추가 매수/매도 내역과 현재 포지션 상태를 분석하여, \
            초기 투자 원칙을 잘 지키고 있는지 점검하고 향후 시장 변동성에 대비한 대응 전략(nextActions)을 제시해 주세요.

            분석 대상 데이터:
            {contextJson}""";

    // 2. 최초 매수 진입 피드백
    public static final String ENTRY_PROMPT =
            """
            사용자가 신규 진입(ENTRY)을 완료했습니다. 전달된 JSON 데이터의 'marketContext'와 첫 'executions' 데이터를 분석하여 \
            진입 시점의 시장 상황(20일 고점/저점 대비 위치 등)이 적절했는지, 투자 이유(investmentReason)가 논리적인지, \
            계획된 손절가(plannedStopLossPrice)가 리스크 관리 차원에서 적합한지 평가해 주세요.

            분석 대상 데이터:
            {contextJson}""";

    // 3. 포지션 종료 리뷰 피드백
    public static final String POSITION_REVIEW_PROMPT =
            """
            규칙 기반 진단 결과를 종합적으로 분석해 주세요. \
            수익/손실 원인은 closedInfo와 executionSummary를 근거로 객관적으로 분석하고, \
            반복된 습관은 diagnosisSummary의 occurrenceCount를 근거로 지적해 주세요. \
            다음 트레이딩에 적용할 수 있는 구체적인 개선점(improvements)과 \
            스스로 돌아볼 수 있는 회고 질문(reflectionQuestions)을 포함해 주세요.

            분석 대상 데이터:
            {contextJson}""";
}