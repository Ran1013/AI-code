package com.ran.aicode.config;

import com.ran.aicode.model.MultiFallbackStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
@Slf4j
public class ReasoningStreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    private StreamingChatModelConfig.FallbackConfig fallbacks =
            new StreamingChatModelConfig.FallbackConfig();

    @Bean
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        StreamingChatModel primary = createPrimaryModel();
        if (fallbacks == null || !Boolean.TRUE.equals(fallbacks.getEnabled())) {
            return primary;
        }
        StreamingChatModel secondary = createSecondaryModel();
        StreamingChatModel tertiary = createTertiaryModel();
        return new MultiFallbackStreamingChatModel(primary, secondary, tertiary, fallbacks, modelName);
    }

    private StreamingChatModel createPrimaryModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    private StreamingChatModel createSecondaryModel() {
        StreamingChatModelConfig.SecondaryConfig config = fallbacks.getSecondary();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        if (config.getBaseUrl() == null || config.getApiKey() == null || config.getModelName() == null) {
            return null;
        }
        return OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : this.maxTokens)
                .temperature(config.getTemperature() != null ? config.getTemperature() : this.temperature)
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses())
                .build();
    }

    private StreamingChatModel createTertiaryModel() {
        StreamingChatModelConfig.TertiaryConfig config = fallbacks.getTertiary();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        if (config.getBaseUrl() == null || config.getApiKey() == null || config.getModelName() == null) {
            return null;
        }
        return OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : this.maxTokens)
                .temperature(config.getTemperature() != null ? config.getTemperature() : this.temperature)
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses())
                .build();
    }
}
