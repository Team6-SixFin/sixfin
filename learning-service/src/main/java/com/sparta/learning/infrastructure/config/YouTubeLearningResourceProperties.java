package com.sparta.learning.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "learning.resources.youtube")
public class YouTubeLearningResourceProperties {

    private boolean enabled;
    private String apiKey;

    @NotBlank
    private String baseUrl = "https://www.googleapis.com/youtube/v3";

    @Min(1)
    @Max(50)
    private int maxResults = 20;
    private String relevanceLanguage = "ko";
    private String regionCode = "KR";
}
