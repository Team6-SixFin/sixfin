package com.sparta.learning.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// AI가 응답하는 JSON 포맷을 매핑할 레코드
@Schema(description = "매매 기록과 규칙 진단을 바탕으로 생성한 AI 피드백")
public record AiFeedbackResponse(
        @Schema(
                description = "목록 화면에도 사용하는 한 문장 요약",
                example = "손절 계획은 세웠지만 최근 고점 부근에서 매수해 진입 시점에 주의가 필요합니다."
        )
        String summary,

        @Schema(
                description = "체결 흐름과 진단 결과를 종합한 설명",
                example = "매수 전에 손절가와 투자 근거를 기록했지만 최근 20일 가격 범위의 상단에서 진입했습니다."
        )
        String overview,

        @Schema(
                description = "진단 결과에서 확인된 잘한 행동",
                example = "[\"매수 전에 계획 손절가를 설정했습니다.\", \"투자 근거를 기록했습니다.\"]"
        )
        List<String> strengths,

        @Schema(
                description = "개선이 필요한 매매 행동",
                example = "[\"최근 급등 이후 고점 부근에서 진입한 점을 점검해 보세요.\"]"
        )
        List<String> improvements,

        @Schema(
                description = "현재 또는 다음 거래에서 실행할 구체적인 행동",
                example = "[\"다음 매수 전 20일 가격 범위에서 현재가 위치를 확인하세요.\"]"
        )
        List<String> nextActions,

        @Schema(
                description = "사용자가 자신의 판단을 돌아볼 수 있는 선택적 질문",
                example = "[\"가격이 급등하지 않았더라도 같은 근거로 매수했을까요?\"]",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        List<String> reflectionQuestions
) {}
