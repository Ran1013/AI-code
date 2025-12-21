package com.ran.aicode.model;

import com.ran.aicode.config.ChatModelConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多级备用模型包装器（ChatModel版本）
 * 用于路由模型的非流式调用
 */
@Slf4j
public class MultiFallbackChatModel implements ChatModel {

    private final ChatModel primaryModel;
    private final ChatModel secondaryModel;
    private final ChatModel tertiaryModel;

    // 当前使用的模型级别
    private volatile MultiFallbackStreamingChatModel.ModelLevel currentLevel = MultiFallbackStreamingChatModel.ModelLevel.PRIMARY;
    private volatile long lastSwitchTime = 0L;

    // 切换统计
    private final AtomicLong switchCount = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public MultiFallbackChatModel(
            ChatModel primaryModel,
            ChatModel secondaryModel,
            ChatModel tertiaryModel) {
        this.primaryModel = primaryModel;
        this.secondaryModel = secondaryModel;
        this.tertiaryModel = tertiaryModel;

        log.info("多级备用ChatModel初始化完成 - 主: {}, 次级: {}, 终极: {}",
                primaryModel != null ? "启用" : "未启用",
                secondaryModel != null ? "启用" : "未启用",
                tertiaryModel != null ? "启用" : "未启用");
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        // 对于非流式调用，简单使用主模型
        try {
            return primaryModel.chat(chatRequest);
        } catch (Exception e) {
            log.warn("非流式调用失败，尝试使用备用模型: {}", e.getMessage());

            // 尝试次级备用
            if (secondaryModel != null) {
                try {
                    return secondaryModel.chat(chatRequest);
                } catch (Exception e2) {
                    log.warn("次级备用模型也失败: {}", e2.getMessage());
                }
            }

            // 尝试终极备用
            if (tertiaryModel != null) {
                try {
                    return tertiaryModel.chat(chatRequest);
                } catch (Exception e3) {
                    log.warn("终极备用模型也失败: {}", e3.getMessage());
                }
            }

                throw new RuntimeException("所有AI模型都不可用", e);
        }
    }

    /**
     * 获取当前模型级别
     */
    public MultiFallbackStreamingChatModel.ModelLevel getCurrentLevel() {
        return currentLevel;
    }

    /**
     * 获取切换次数
     */
    public long getSwitchCount() {
        return switchCount.get();
    }
}