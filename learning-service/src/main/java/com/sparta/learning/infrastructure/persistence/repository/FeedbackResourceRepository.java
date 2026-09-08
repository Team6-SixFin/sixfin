package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.FeedbackResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackResourceRepository extends JpaRepository<FeedbackResource, Long> {

    List<FeedbackResource> findAllByFeedbackIdOrderByDisplayOrderAscIdAsc(Long feedbackId);
}
