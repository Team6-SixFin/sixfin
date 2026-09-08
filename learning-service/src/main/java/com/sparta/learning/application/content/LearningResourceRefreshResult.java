package com.sparta.learning.application.content;

/** 한 번의 외부 후보 갱신으로 생성·수정된 자료 수입니다. */
public record LearningResourceRefreshResult(
        int createdCount,
        int updatedCount
) {

    public int totalCount() {
        return createdCount + updatedCount;
    }

    public static LearningResourceRefreshResult empty() {
        return new LearningResourceRefreshResult(0, 0);
    }
}
