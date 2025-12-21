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
import org.springframework.core.env.Environment;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
@Slf4j
public class StreamingChatModelConfig {

    // 原有主模型配置
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

    // 新增多级备用配置
    private FallbackConfig fallbacks = new FallbackConfig();

    @Data
    public static class FallbackConfig {
        private Boolean enabled = false;
        private Integer recoveryIntervalMinutes = 15;
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

    private final Environment environment;

    public StreamingChatModelConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype() {
        // 创建主模型
        StreamingChatModel primary = createPrimaryModel();

        // 如果没有启用备用，直接返回主模型
        if (fallbacks == null || !fallbacks.getEnabled()) {
            log.info("备用模型未启用，使用主模型: {}", modelName);
            return primary;
        }

        // 创建备用模型
        StreamingChatModel secondary = null;
        if (fallbacks.getSecondary() != null && fallbacks.getSecondary().getEnabled()) {
            secondary = createSecondaryModel();
            log.info("次级备用模型已启用: {}", fallbacks.getSecondary().getModelName());
        }

        StreamingChatModel tertiary = null;
        if (fallbacks.getTertiary() != null && fallbacks.getTertiary().getEnabled()) {
            tertiary = createTertiaryModel();
            log.info("终极备用模型已启用: {}", fallbacks.getTertiary().getModelName());
        }

        return new MultiFallbackStreamingChatModel(
                primary, secondary, tertiary, fallbacks);
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
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : this.maxTokens)
                .temperature(config.getTemperature() != null ? config.getTemperature() : this.temperature)
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses());

        if (config.getTimeoutMs() != null && config.getTimeoutMs() > 0) {
            builder.timeout(Duration.ofMillis(config.getTimeoutMs()));
        }

        return builder.build();
    }

    private StreamingChatModel createTertiaryModel() {
        TertiaryConfig config = fallbacks.getTertiary();
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : this.maxTokens)
                .temperature(config.getTemperature() != null ? config.getTemperature() : this.temperature)
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses());

        if (config.getTimeoutMs() != null && config.getTimeoutMs() > 0) {
            builder.timeout(Duration.ofMillis(config.getTimeoutMs()));
        }

        return builder.build();
    }
}
