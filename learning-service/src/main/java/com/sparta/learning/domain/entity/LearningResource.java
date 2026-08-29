package com.sparta.learning.domain.entity;

import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(
        name = "learning_resources",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_learning_resource_rule_provider_external",
                columnNames = {"rule_code", "provider", "external_id"}
        ),
        indexes = @Index(name = "idx_learning_resource_expires_at", columnList = "expires_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "search_query", nullable = false, length = 300)
    private String searchQuery;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private ResourceProvider provider;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "channel_id", length = 100)
    private String channelId;

    @Column(name = "channel_name", length = 200)
    private String channelName;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "view_count")
    private Long viewCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResourceStatus status;

    @CreationTimestamp
    @Column(name = "searched_at", nullable = false)
    private OffsetDateTime searchedAt;

    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Builder
    private LearningResource(
            String ruleCode,
            String searchQuery,
            ResourceProvider provider,
            String externalId,
            String title,
            String description,
            String channelId,
            String channelName,
            String url,
            String thumbnailUrl,
            OffsetDateTime publishedAt,
            Integer durationSeconds,
            Long viewCount,
            OffsetDateTime searchedAt,
            OffsetDateTime lastVerifiedAt,
            OffsetDateTime expiresAt
    ) {
        this.ruleCode = ruleCode;
        this.searchQuery = searchQuery;
        this.provider = provider;
        this.externalId = externalId;
        this.title = title;
        this.description = description;
        this.channelId = channelId;
        this.channelName = channelName;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;
        this.durationSeconds = durationSeconds;
        this.viewCount = viewCount;
        this.status = ResourceStatus.ACTIVE;
        this.lastVerifiedAt = lastVerifiedAt;
        this.expiresAt = expiresAt;
    }

    public void markUnavailable(OffsetDateTime verifiedAt) {
        this.status = ResourceStatus.UNAVAILABLE;
        this.lastVerifiedAt = verifiedAt;
    }
}
