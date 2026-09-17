package com.sparta.trading.global.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Slice;

@Getter
@Builder
public class SliceInfo {

    private String paginationType;
    private int page;
    private int size;
    private boolean hasNext;

    public static SliceInfo of(Slice<?> slice) {
        return SliceInfo.builder()
                .paginationType("OFFSET")
                .page(slice.getNumber())
                .size(slice.getSize())
                .hasNext(slice.hasNext())
                .build();
    }
}
