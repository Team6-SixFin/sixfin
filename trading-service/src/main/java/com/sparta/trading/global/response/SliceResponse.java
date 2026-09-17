package com.sparta.trading.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SliceResponse<T> {

    private List<T> content;
    private SliceInfo pageInfo;
    private Object summary;

    public static <T> SliceResponse<T> of(Slice<T> slice) {
        return SliceResponse.<T>builder()
                .content(slice.getContent())
                .pageInfo(SliceInfo.of(slice))
                .build();
    }

    public static <T> SliceResponse<T> of(Object summary, Slice<T> slice) {
        return SliceResponse.<T>builder()
                .content(slice.getContent())
                .summary(summary)
                .pageInfo(SliceInfo.of(slice))
                .build();
    }
}
