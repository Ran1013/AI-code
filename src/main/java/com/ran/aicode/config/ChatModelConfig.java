package com.ran.aicode.config;

import com.ran.aicode.model.MultiFallbackChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * ChatModel配置类
 * 支持三级备用模型机制
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
@Data
@Slf4j
public class ChatModelConfig {

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
    private FallbackConfig fallbacks = new FallbackConfig();

    private final Environment environment;

    @Data
    public static class FallbackConfig {
        private Boolean enabled = false;
        private Integer recoveryIntervalMinutes = 30;  // 30 分钟后再尝试主模型
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

    public ChatModelConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 创建带有三级备用机制的ChatModel
     * 使用自定义名称避免与LangChain4j自动配置冲突
     */
    @Bean(name = "multiFallbackOpenAiChatModel")
    @Primary
    public ChatModel multiFallbackOpenAiChatModel() {
        // 创建主模型
        ChatModel primary = createPrimaryModel();

        // 如果没有启用备用，直接返回主模型
        if (fallbacks == null || !fallbacks.getEnabled()) {
            log.info("ChatModel备用未启用，使用主模型: {}", modelName);
            return primary;
        }

        // 创建备用模型
        ChatModel secondary = null;
        if (fallbacks.getSecondary() != null && fallbacks.getSecondary().getEnabled()) {
            secondary = createSecondaryModel();
            log.info("ChatModel次级备用模型已启用: {}", fallbacks.getSecondary().getModelName());
        }

        ChatModel tertiary = null;
        if (fallbacks.getTertiary() != null && fallbacks.getTertiary().getEnabled()) {
            tertiary = createTertiaryModel();
            log.info("ChatModel终极备用模型已启用: {}", fallbacks.getTertiary().getModelName());
        }

        return new MultiFallbackChatModel(
                primary, secondary, tertiary, fallbacks, modelName);
    }

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

    private ChatModel createSecondaryModel() {
        SecondaryConfig config = fallbacks.getSecondary();
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
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

    private ChatModel createTertiaryModel() {
        TertiaryConfig config = fallbacks.getTertiary();
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
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