package com.ran.aicode.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
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

    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype() {
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
}