package com.ran.aicode.model;

import com.ran.aicode.config.StreamingChatModelConfig;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多级备用模型包装器
 * 支持主模型 -> 次级备用 -> 终极备用的三级切换
 */
@Slf4j
public class MultiFallbackStreamingChatModel implements StreamingChatModel {

    /**
     * 模型级别枚举
     */
    public enum ModelLevel {
        PRIMARY("primary", "主模型"),
        SECONDARY("secondary", "次级备用"),
        TERTIARY("tertiary", "终极备用");

        private final String code;
        private final String desc;

        ModelLevel(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() { return code; }
        public String getDesc() { return desc; }
    }

    private final StreamingChatModel primaryModel;
    private final StreamingChatModel secondaryModel;
    private final StreamingChatModel tertiaryModel;
    private final StreamingChatModelConfig.FallbackConfig config;

    // 当前使用的模型级别
    private volatile ModelLevel currentLevel = ModelLevel.PRIMARY;
    private volatile long lastSwitchTime = 0L;

    // 切换统计
    private final AtomicLong switchCount = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public MultiFallbackStreamingChatModel(
            StreamingChatModel primaryModel,
            StreamingChatModel secondaryModel,
            StreamingChatModel tertiaryModel,
            StreamingChatModelConfig.FallbackConfig config) {
        this.primaryModel = primaryModel;
        this.secondaryModel = secondaryModel;
        this.tertiaryModel = tertiaryModel;
        this.config = config;

        log.info("多级备用模型初始化完成 - 主: {}, 次级: {}, 终极: {}",
                primaryModel != null ? "启用" : "未启用",
                secondaryModel != null ? "启用" : "未启用",
                tertiaryModel != null ? "启用" : "未启用");
    }

    @Override
    public void chat(String userMessage, StreamingChatResponseHandler handler) {
        // 检查是否到了恢复时间
        checkRecoveryTime();

        List<ModelLevel> levelsToTry = getLevelsToTry();
        Exception lastException = null;

        for (ModelLevel level : levelsToTry) {
            try {
                StreamingChatModel model = getModelByLevel(level);
                if (model == null) {
                    log.warn("模型 {} 未配置，跳过", level.getDesc());
                    continue;
                }

                log.debug("尝试使用模型: {}", level.getDesc());
                model.chat(userMessage, handler);

                // 成功则重置失败计数
                consecutiveFailures.set(0);

                // 如果回到了主模型，记录恢复
                if (level == ModelLevel.PRIMARY && currentLevel != ModelLevel.PRIMARY) {
                    logSwitch(level, "主模型恢复");
                    currentLevel = level;
                }

                return;

            } catch (Exception e) {
                lastException = e;
                consecutiveFailures.incrementAndGet();

                if (shouldSwitchModel(e, level)) {
                    ModelLevel nextLevel = getNextLevel(level);
                    if (nextLevel != null) {
                        logSwitch(nextLevel, e.getMessage());
                        currentLevel = nextLevel;
                        lastSwitchTime = System.currentTimeMillis();

                        // 短暂延迟后重试
                        if (level != levelsToTry.get(levelsToTry.size() - 1)) {
                            try {
                                Thread.sleep(100); // 100ms延迟
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        continue;
                    }
                }

                log.warn("模型 {} 调用失败: {}", level.getDesc(), e.getMessage());
            }
        }

        throw new RuntimeException("所有AI模型都不可用", lastException);
    }

  
    /**
     * 检查是否应该切换模型
     * 基于Codex的精准异常识别逻辑
     */
    private boolean shouldSwitchModel(Exception e, ModelLevel currentLevel) {
        if (!config.getEnabled()) {
            return false;
        }

        // 如果已经是终极备用，不能再切换了
        if (currentLevel == ModelLevel.TERTIARY) {
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
            if (msg.contains("429")) {
                log.info("检测到请求频率限制，触发模型切换");
                return true; // 请求频率限制
            }
            if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) {
                log.info("检测到服务端错误，触发模型切换");
                return true; // 服务端错误
            }
        }

        // 关键词匹配（中英文）- 基于Codex的设计
        String[] quotaKeywords = {
            "quota", "额度", "limit", "配额", "exceeded", "超出",
            "insufficient", "不足", "payment", "付费", "billing", "账单",
            "rate limit", "频率限制", "throttle", "节流"
        };

        for (String keyword : quotaKeywords) {
            if (errorMessage.contains(keyword)) {
                log.info("检测到额度相关关键词: {}，触发模型切换", keyword);
                return true;
            }
        }

        // 连续失败3次也切换
        int failures = consecutiveFailures.get();
        if (failures >= 3) {
            log.info("连续失败{}次，触发模型切换", failures);
            return true;
        }

        return false;
    }

    /**
     * 获取下一个可用的模型级别
     */
    private ModelLevel getNextLevel(ModelLevel currentLevel) {
        return switch (currentLevel) {
            case PRIMARY -> config.getSecondary() != null && config.getSecondary().getEnabled()
                    ? ModelLevel.SECONDARY : null;
            case SECONDARY -> config.getTertiary() != null && config.getTertiary().getEnabled()
                    ? ModelLevel.TERTIARY : null;
            case TERTIARY -> null; // 没有更多备用了
        };
    }

    /**
     * 获取要尝试的模型级别列表
     */
    private List<ModelLevel> getLevelsToTry() {
        List<ModelLevel> levels = new ArrayList<>();
        levels.add(currentLevel);

        // 总是尝试主模型（如果当前不是主模型）
        if (currentLevel != ModelLevel.PRIMARY) {
            levels.add(ModelLevel.PRIMARY);
        }

        // 添加其他可用的备用模型
        if (currentLevel != ModelLevel.SECONDARY &&
            config.getSecondary() != null && config.getSecondary().getEnabled()) {
            levels.add(ModelLevel.SECONDARY);
        }

        if (currentLevel != ModelLevel.TERTIARY &&
            config.getTertiary() != null && config.getTertiary().getEnabled()) {
            levels.add(ModelLevel.TERTIARY);
        }

        return levels;
    }

    /**
     * 检查恢复时间
     */
    private void checkRecoveryTime() {
        if (currentLevel == ModelLevel.PRIMARY) {
            return;
        }

        long timeSinceSwitch = System.currentTimeMillis() - lastSwitchTime;
        long recoveryIntervalMs = config.getRecoveryIntervalMinutes() * 60 * 1000L;

        if (timeSinceSwitch >= recoveryIntervalMs) {
            log.info("达到恢复间隔{}分钟，尝试切换回主模型", config.getRecoveryIntervalMinutes());
            currentLevel = ModelLevel.PRIMARY;
        }
    }

    /**
     * 记录模型切换
     */
    private void logSwitch(ModelLevel newLevel, String reason) {
        long totalSwitches = switchCount.incrementAndGet();
        log.info("模型切换: {} -> {}, 总次数: {}, 原因: {}",
                currentLevel.getDesc(), newLevel.getDesc(), totalSwitches, reason);

        // 触发告警（如果切换频繁）
        if (config.getMonitoring().getEnabled() &&
            totalSwitches >= config.getMonitoring().getSwitchAlertThreshold()) {
            log.warn("模型切换频繁警告：已切换{}次，阈值{}",
                    totalSwitches, config.getMonitoring().getSwitchAlertThreshold());
            // TODO: 发送告警通知
        }
    }

    /**
     * 根据级别获取模型实例
     */
    private StreamingChatModel getModelByLevel(ModelLevel level) {
        return switch (level) {
            case PRIMARY -> primaryModel;
            case SECONDARY -> secondaryModel;
            case TERTIARY -> tertiaryModel;
        };
    }

    /**
     * 获取当前模型级别
     */
    public ModelLevel getCurrentLevel() {
        return currentLevel;
    }

    /**
     * 获取切换次数
     */
    public long getSwitchCount() {
        return switchCount.get();
    }

    /**
     * 手动切换到指定模型级别
     */
    public boolean switchToLevel(ModelLevel targetLevel) {
        StreamingChatModel model = getModelByLevel(targetLevel);
        if (model == null) {
            log.warn("目标模型 {} 未配置，无法切换", targetLevel.getDesc());
            return false;
        }

        ModelLevel oldLevel = currentLevel;
        currentLevel = targetLevel;
        lastSwitchTime = System.currentTimeMillis();

        log.info("手动切换模型: {} -> {}", oldLevel.getDesc(), targetLevel.getDesc());
        return true;
    }

    /**
     * 重置到主模型
     */
    public void resetToPrimary() {
        if (primaryModel != null) {
            currentLevel = ModelLevel.PRIMARY;
            lastSwitchTime = 0;
            consecutiveFailures.set(0);
            log.info("已重置到主模型");
        }
    }
}