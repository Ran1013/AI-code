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
    private final ChatModelConfig.FallbackConfig config;
    private final String primaryModelName;

    // 当前使用的模型级别
    private volatile MultiFallbackStreamingChatModel.ModelLevel currentLevel = MultiFallbackStreamingChatModel.ModelLevel.PRIMARY;
    private volatile long lastSwitchTime = 0L;

    // 切换统计
    private final AtomicLong switchCount = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public MultiFallbackChatModel(
            ChatModel primaryModel,
            ChatModel secondaryModel,
            ChatModel tertiaryModel,
            ChatModelConfig.FallbackConfig config,
            String primaryModelName) {
        this.primaryModel = primaryModel;
        this.secondaryModel = secondaryModel;
        this.tertiaryModel = tertiaryModel;
        this.config = config;
        this.primaryModelName = primaryModelName;

        log.info("多级备用ChatModel初始化完成 - 主: {}, 次级: {}, 终极: {}",
                primaryModel != null ? "启用" : "未启用",
                secondaryModel != null ? "启用" : "未启用",
                tertiaryModel != null ? "启用" : "未启用");
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        checkRecoveryTime();
        List<MultiFallbackStreamingChatModel.ModelLevel> levelsToTry = getLevelsToTry();
        Exception lastException = null;

        for (MultiFallbackStreamingChatModel.ModelLevel level : levelsToTry) {
            try {
                ChatModel model = getModelByLevel(level);
                if (model == null) {
                    log.warn("模型 {} 未配置，跳过", level.getDesc());
                    continue;
                }

                ChatResponse response = model.chat(chatRequest);
                consecutiveFailures.set(0);

                if (level == MultiFallbackStreamingChatModel.ModelLevel.PRIMARY && currentLevel != MultiFallbackStreamingChatModel.ModelLevel.PRIMARY) {
                    logSwitch(level, "主模型恢复");
                    currentLevel = level;
                }
                return response;

            } catch (Exception e) {
                lastException = e;
                consecutiveFailures.incrementAndGet();

                if (shouldSwitchModel(e, level)) {
                    MultiFallbackStreamingChatModel.ModelLevel nextLevel = getNextLevel(level);
                    if (nextLevel != null) {
                        logSwitch(nextLevel, e.getMessage());
                        currentLevel = nextLevel;
                        lastSwitchTime = System.currentTimeMillis();
                        continue;
                    }
                }
                log.warn("模型 {} 调用失败: {}", level.getDesc(), e.getMessage());
            }
        }
        throw new RuntimeException("所有AI模型都不可用", lastException);
    }

    private void checkRecoveryTime() {
        if (currentLevel == MultiFallbackStreamingChatModel.ModelLevel.PRIMARY) {
            return;
        }

        long timeSinceSwitch = System.currentTimeMillis() - lastSwitchTime;
        long recoveryIntervalMs = config.getRecoveryIntervalMinutes() * 60 * 1000L;

        if (timeSinceSwitch >= recoveryIntervalMs) {
            log.info("达到恢复间隔{}分钟，尝试切换回主模型", config.getRecoveryIntervalMinutes());
            currentLevel = MultiFallbackStreamingChatModel.ModelLevel.PRIMARY;
        }
    }

    private List<MultiFallbackStreamingChatModel.ModelLevel> getLevelsToTry() {
        List<MultiFallbackStreamingChatModel.ModelLevel> levels = new ArrayList<>();
        levels.add(currentLevel);

        // 总是尝试主模型（如果当前不是主模型）
        if (currentLevel != MultiFallbackStreamingChatModel.ModelLevel.PRIMARY) {
            levels.add(MultiFallbackStreamingChatModel.ModelLevel.PRIMARY);
        }

        // 添加其他可用的备用模型
        if (currentLevel != MultiFallbackStreamingChatModel.ModelLevel.SECONDARY &&
            config.getSecondary() != null && config.getSecondary().getEnabled()) {
            levels.add(MultiFallbackStreamingChatModel.ModelLevel.SECONDARY);
        }

        if (currentLevel != MultiFallbackStreamingChatModel.ModelLevel.TERTIARY &&
            config.getTertiary() != null && config.getTertiary().getEnabled()) {
            levels.add(MultiFallbackStreamingChatModel.ModelLevel.TERTIARY);
        }

        return levels;
    }

    private boolean shouldSwitchModel(Exception e, MultiFallbackStreamingChatModel.ModelLevel currentLevel) {
        if (!config.getEnabled()) {
            return false;
        }

        // 如果已经是终极备用，不能再切换了
        if (currentLevel == MultiFallbackStreamingChatModel.ModelLevel.TERTIARY) {
            return false;
        }

        String errorMessage = e.getMessage().toLowerCase();

        // HTTP状态码判断
        if (e.getMessage() != null) {
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("402") || msg.contains("401")) {
                log.info("检测到额度或认证问题，触发模型切换");
                return true; // 额度或认证问题
            }
            // 认证失败关键词检测
            if (msg.contains("authentication") || msg.contains("auth") || msg.contains("token")) {
                log.info("检测到认证失败关键词，触发模型切换");
                return true;
            }
            if (msg.contains("429")) {
                log.info("检测到请求频率限制，触发模型切换");
                return true; // 频率限制
            }
            if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) {
                log.info("检测到服务器错误，触发模型切换");
                return true; // 服务器错误
            }
        }

        // 网络超时或连接错误
        if (e.getCause() != null &&
            (e.getCause() instanceof java.net.SocketTimeoutException ||
             e.getCause() instanceof java.net.ConnectException ||
             e.getCause() instanceof java.net.UnknownHostException)) {
            log.info("检测到网络错误，触发模型切换");
            return true;
        }

        // 模型不可用错误检测 - 模型被删除、不存在或不支持
        String[] modelUnavailableKeywords = {
            "no provider", "no provider supported",
            "not supported", "does not support",
            "not found", "model not found",
            "invalid model", "invalid model id",
            "model not available", "unavailable",
            "does not exist", "not exist"
        };

        for (String keyword : modelUnavailableKeywords) {
            if (errorMessage.contains(keyword)) {
                log.info("检测到模型不可用关键词: {}，触发模型切换", keyword);
                return true;
            }
        }

        // 连续失败次数超过阈值
        if (consecutiveFailures.get() >= 3) {
            log.info("连续失败{}次，触发模型切换", consecutiveFailures.get());
            return true;
        }

        return false;
    }

    private MultiFallbackStreamingChatModel.ModelLevel getNextLevel(MultiFallbackStreamingChatModel.ModelLevel currentLevel) {
        return switch (currentLevel) {
            case PRIMARY -> config.getSecondary() != null && config.getSecondary().getEnabled()
                    ? MultiFallbackStreamingChatModel.ModelLevel.SECONDARY : null;
            case SECONDARY -> config.getTertiary() != null && config.getTertiary().getEnabled()
                    ? MultiFallbackStreamingChatModel.ModelLevel.TERTIARY : null;
            case TERTIARY -> null; // 没有更多备用了
        };
    }

    private ChatModel getModelByLevel(MultiFallbackStreamingChatModel.ModelLevel level) {
        return switch (level) {
            case PRIMARY -> primaryModel;
            case SECONDARY -> secondaryModel;
            case TERTIARY -> tertiaryModel;
        };
    }

    private void logSwitch(MultiFallbackStreamingChatModel.ModelLevel newLevel, String reason) {
        long totalSwitches = switchCount.incrementAndGet();

        // 获取目标模型的名称
        String targetModelName = getModelName(newLevel);

        log.info("模型切换: {} -> {}, 模型名称: {}, 总次数: {}, 原因: {}",
                currentLevel.getDesc(), newLevel.getDesc(), targetModelName, totalSwitches, reason);

        // 触发告警（如果切换频繁）
        if (config.getMonitoring().getEnabled() &&
            totalSwitches >= config.getMonitoring().getSwitchAlertThreshold()) {
            log.warn("模型切换频繁警告：已切换{}次，阈值{}",
                    totalSwitches, config.getMonitoring().getSwitchAlertThreshold());
            // TODO: 发送告警通知
        }
    }

    /**
     * 获取指定级别模型的名称
     */
  private String getModelName(MultiFallbackStreamingChatModel.ModelLevel level) {
        switch (level) {
            case PRIMARY:
                return primaryModelName != null ? primaryModelName : "主模型";
            case SECONDARY:
                return config.getSecondary() != null && config.getSecondary().getModelName() != null
                        ? config.getSecondary().getModelName() : "次级备用";
            case TERTIARY:
                return config.getTertiary() != null && config.getTertiary().getModelName() != null
                        ? config.getTertiary().getModelName() : "终极备用";
            default:
                return "未知模型";
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