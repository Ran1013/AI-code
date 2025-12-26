package com.ran.aicode.config;

import com.ran.aicode.model.MultiFallbackChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.routing-chat-model")
@Data
@Slf4j
public class RoutingAiModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    private Long timeoutMs;

    // 复用 ChatModelConfig 的 FallbackConfig，保持配置结构一致
    private ChatModelConfig.FallbackConfig fallbacks = new ChatModelConfig.FallbackConfig();

    /**
     * 创建用于路由判断的ChatModel
     * 支持多级保底机制
     */
    @Bean
    @Scope("prototype")
    public ChatModel routingChatModelPrototype() {
        // 创建主模型
        ChatModel primary = createPrimaryModel();

        // 如果没有启用备用，直接返回主模型（保持向后兼容）
        if (fallbacks == null || !Boolean.TRUE.equals(fallbacks.getEnabled())) {
            return primary;
        }

        // 创建次级保底模型
        ChatModel secondary = createSecondaryModel();
        if (secondary != null) {
            log.info("RoutingChatModel次级保底模型已启用: {}",
                    fallbacks.getSecondary().getModelName());
        }

        // 创建终极保底模型
        ChatModel tertiary = createTertiaryModel();
        if (tertiary != null) {
            log.info("RoutingChatModel终极保底模型已启用: {}",
                    fallbacks.getTertiary().getModelName());
        }

        return new MultiFallbackChatModel(primary, secondary, tertiary, fallbacks, modelName);
    }

    /**
     * 创建主模型
     */
    private ChatModel createPrimaryModel() {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
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

    /**
     * 创建次级保底模型
     */
    private ChatModel createSecondaryModel() {
        ChatModelConfig.SecondaryConfig config = fallbacks.getSecondary();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                // 次级模型未配置的参数回落到主模型
                .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : this.maxTokens)
                .temperature(config.getTemperature() != null ? config.getTemperature() : this.temperature)
                .logRequests(config.getLogRequests())
                .logResponses(config.getLogResponses());

        if (config.getTimeoutMs() != null && config.getTimeoutMs() > 0) {
            builder.timeout(Duration.ofMillis(config.getTimeoutMs()));
        }

        return builder.build();
    }

    /**
     * 创建终极保底模型
     */
    private ChatModel createTertiaryModel() {
        ChatModelConfig.TertiaryConfig config = fallbacks.getTertiary();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                // 终极模型未配置的参数回落到主模型
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
