package com.ran.aicode.config;

import com.ran.aicode.model.MultiFallbackStreamingChatModel;
import com.ran.aicode.config.StreamingChatModelConfig;
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
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
@Slf4j
public class ReasoningStreamingChatModelConfig {

    // 原有主模型配置
    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    private Long timeoutMs;

    // 新增多级备用配置
    private StreamingChatModelConfig.FallbackConfig fallbacks = new StreamingChatModelConfig.FallbackConfig();

    private final Environment environment;

    public ReasoningStreamingChatModelConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        // 创建主模型
        StreamingChatModel primary = createPrimaryModel();

        // 如果没有启用备用，直接返回主模型
        if (fallbacks == null || !fallbacks.getEnabled()) {
            log.info("推理模型备用未启用，使用主模型: {}", modelName);
            return primary;
        }

        // 创建备用模型
        StreamingChatModel secondary = null;
        if (fallbacks.getSecondary() != null && fallbacks.getSecondary().getEnabled()) {
            secondary = createSecondaryModel();
            log.info("推理次级备用模型已启用: {}", fallbacks.getSecondary().getModelName());
        }

        StreamingChatModel tertiary = null;
        if (fallbacks.getTertiary() != null && fallbacks.getTertiary().getEnabled()) {
            tertiary = createTertiaryModel();
            log.info("推理终极备用模型已启用: {}", fallbacks.getTertiary().getModelName());
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
        StreamingChatModelConfig.SecondaryConfig config = fallbacks.getSecondary();
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
        StreamingChatModelConfig.TertiaryConfig config = fallbacks.getTertiary();
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
