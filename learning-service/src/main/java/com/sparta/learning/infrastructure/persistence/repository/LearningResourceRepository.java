package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.ResourceProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {

    List<LearningResource> findAllByRuleCodeAndProviderAndExternalIdIn(
            String ruleCode,
            ResourceProvider provider,
            Collection<String> externalIds
    );
}
