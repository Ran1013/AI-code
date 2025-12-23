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

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
@Slf4j
public class StreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

    /**
     * 流式请求超时时间（毫秒）。用于底层 HTTP connect/read 超时。
     * application-prod.yml: langchain4j.open-ai.streaming-chat-model.timeout-ms
     */
    private Long timeoutMs;

    private FallbackConfig fallbacks = new FallbackConfig();

    @Data
    public static class FallbackConfig {
        private Boolean enabled = false;
        private Integer recoveryIntervalMinutes = 30;
        private SecondaryConfig secondary;
        private TertiaryConfig tertiary;
        private MonitoringConfig monitoring = new MonitoringConfig();
    }

    @Data
    public static class SecondaryConfig {
        private Boolean enabled = false;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer maxTokens;
        private Double temperature;
        private Long timeoutMs = 30000L;
        private Boolean logRequests = false;
        private Boolean logResponses = false;
    }

    @Data
    public static class TertiaryConfig {
        private Boolean enabled = false;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer maxTokens;
        private Double temperature;
        private Long timeoutMs = 45000L;
        private Boolean logRequests = false;
        private Boolean logResponses = false;
    }

    @Data
    public static class MonitoringConfig {
        private Boolean enabled = true;
        private Integer switchAlertThreshold = 5;
        private String logLevel = "INFO";
    }

    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype() {
        StreamingChatModel primary = createPrimaryModel();

        if (fallbacks == null || !Boolean.TRUE.equals(fallbacks.getEnabled())) {
            return primary;
        }

        StreamingChatModel secondary = createSecondaryModel();
        StreamingChatModel tertiary = createTertiaryModel();

        return new MultiFallbackStreamingChatModel(primary, secondary, tertiary, fallbacks, modelName);
    }

    private StreamingChatModel createPrimaryModel() {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses);

        if (timeoutMs != null && timeoutMs > 0) {
            builder.timeout(Duration.ofMillis(timeoutMs));
        }
        return builder.build();
    }

    private StreamingChatModel createSecondaryModel() {
        SecondaryConfig config = fallbacks.getSecondary();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        if (config.getBaseUrl() == null || config.getApiKey() == null || config.getModelName() == null) {
            // 关键配置缺失，忽略该备用模型
            return null;
        }
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : this.maxTokens)
                .temperature(config.getTemperature() != null ? config.getTemperature() : this.temperature)
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses());

        Long secTimeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : this.timeoutMs;
        if (secTimeout != null && secTimeout > 0) {
            builder.timeout(Duration.ofMillis(secTimeout));
        }
        return builder.build();
    }

    private StreamingChatModel createTertiaryModel() {
        TertiaryConfig config = fallbacks.getTertiary();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        if (config.getBaseUrl() == null || config.getApiKey() == null || config.getModelName() == null) {
            return null;
        }
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : this.maxTokens)
                .temperature(config.getTemperature() != null ? config.getTemperature() : this.temperature)
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses());

        Long terTimeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : this.timeoutMs;
        if (terTimeout != null && terTimeout > 0) {
            builder.timeout(Duration.ofMillis(terTimeout));
        }
        return builder.build();
    }
}
