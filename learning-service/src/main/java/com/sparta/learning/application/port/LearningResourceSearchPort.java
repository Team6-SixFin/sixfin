package com.sparta.learning.application.port;

import com.sparta.learning.application.content.DiscoveredLearningResource;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceType;

import java.util.List;

/** 외부 학습 자료 제공자를 애플리케이션 로직과 분리하는 검색 포트입니다. */
public interface LearningResourceSearchPort {

    ResourceProvider provider();

    ResourceType resourceType();

    List<DiscoveredLearningResource> search(String query, int limit);
}
