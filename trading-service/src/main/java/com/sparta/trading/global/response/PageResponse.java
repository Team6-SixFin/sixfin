package com.sparta.trading.global.response;

import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private PageInfo pageInfo;
    private Object summary;

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageInfo(PageInfo.of(page))
                .build();
    }

    public static <T> PageResponse<T> of(Object summary, Page<T> page){
        return PageResponse.<T>builder()
                .content(page.getContent())
                .summary(summary)
                .pageInfo(PageInfo.of(page))
                .build();
    }

}
