package com.ran.aicode.controller;

import com.ran.aicode.common.BaseResponse;
import com.ran.aicode.common.ResultUtils;
import com.ran.aicode.config.StreamingChatModelConfig;
import com.ran.aicode.model.MultiFallbackChatModel;
import com.ran.aicode.model.MultiFallbackStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI模型管理控制器
 * 提供模型状态查询、手动切换等功能
 */
@RestController
@RequestMapping("/admin/ai-model")
@Tag(name = "AI模型管理", description = "AI模型状态管理和控制")
@Slf4j
public class AiModelManagementController {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 获取所有AI模型的当前状态
     */
    @GetMapping("/status")
    @Operation(summary = "获取AI模型状态", description = "获取所有AI模型的当前使用状态和切换统计")
    public BaseResponse<Map<String, Object>> getModelStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // 获取ChatModel状态（主ChatModel，用于一般用途）
            ChatModel chatModel = getModelByName("multiFallbackOpenAiChatModel");
            if (chatModel instanceof MultiFallbackChatModel) {
                MultiFallbackChatModel multiModel = (MultiFallbackChatModel) chatModel;
                Map<String, Object> chatStatus = new HashMap<>();
                chatStatus.put("currentLevel", multiModel.getCurrentLevel().getDesc());
                chatStatus.put("switchCount", multiModel.getSwitchCount());
                chatStatus.put("modelType", "ChatModel");
                status.put("chat-model", chatStatus);
            }

            // 获取流式模型状态
            StreamingChatModel streamingModel = getModelByPrototype("streamingChatModelPrototype");
            if (streamingModel instanceof MultiFallbackStreamingChatModel) {
                MultiFallbackStreamingChatModel multiModel = (MultiFallbackStreamingChatModel) streamingModel;
                Map<String, Object> streamingStatus = new HashMap<>();
                streamingStatus.put("currentLevel", multiModel.getCurrentLevel().getDesc());
                streamingStatus.put("switchCount", multiModel.getSwitchCount());
                streamingStatus.put("modelType", "StreamingChatModel");
                status.put("streaming-chat-model", streamingStatus);
            }

            // 获取推理模型状态
            StreamingChatModel reasoningModel = getModelByPrototype("reasoningStreamingChatModelPrototype");
            if (reasoningModel instanceof MultiFallbackStreamingChatModel) {
                MultiFallbackStreamingChatModel multiModel = (MultiFallbackStreamingChatModel) reasoningModel;
                Map<String, Object> reasoningStatus = new HashMap<>();
                reasoningStatus.put("currentLevel", multiModel.getCurrentLevel().getDesc());
                reasoningStatus.put("switchCount", multiModel.getSwitchCount());
                reasoningStatus.put("modelType", "ReasoningStreamingChatModel");
                status.put("reasoning-streaming-chat-model", reasoningStatus);
            }

            // 路由模型不使用三级备用，显示基本信息
            ChatModel routingModel = getModelByPrototype("routingChatModelPrototype");
            Map<String, Object> routingStatus = new HashMap<>();
            routingStatus.put("modelType", "RoutingChatModel");
            routingStatus.put("status", "normal");
            routingStatus.put("fallbackSupport", false);
            status.put("routing-chat-model", routingStatus);

            status.put("timestamp", System.currentTimeMillis());
            status.put("success", true);

            return ResultUtils.success(status);

        } catch (Exception e) {
            log.error("获取AI模型状态失败", e);
            status.put("error", e.getMessage());
            status.put("success", false);
            return (BaseResponse<Map<String, Object>>) ResultUtils.error(500, "获取AI模型状态失败: " + e.getMessage());
        }
    }

    /**
     * 重置指定模型到主模型
     */
    @PostMapping("/{modelType}/reset")
    @Operation(summary = "重置模型", description = "将指定模型重置到主模型")
    public BaseResponse<String> resetModelToPrimary(@PathVariable String modelType) {
        try {
            String beanName = getBeanNameByModelType(modelType);
            Object model = getModelByPrototype(beanName);

            if (model instanceof MultiFallbackStreamingChatModel) {
                ((MultiFallbackStreamingChatModel) model).resetToPrimary();
                log.info("已重置 {} 到主模型", modelType);
                return ResultUtils.success("已重置 " + modelType + " 到主模型");
            } else if (model instanceof MultiFallbackChatModel) {
                // ChatModel版本没有resetToPrimary方法，但可以通过日志记录
                log.info("ChatModel类型 {} 暂不支持手动重置", modelType);
                return ResultUtils.success("ChatModel类型 " + modelType + " 暂不支持手动重置");
            }

            return (BaseResponse<String>) ResultUtils.error(400, "模型类型 " + modelType + " 不支持多级备用功能");

        } catch (Exception e) {
            log.error("重置模型 {} 失败", modelType, e);
            return (BaseResponse<String>) ResultUtils.error(500, "重置模型失败: " + e.getMessage());
        }
    }

    /**
     * 手动切换模型到指定级别
     */
    @PostMapping("/{modelType}/switch")
    @Operation(summary = "切换模型", description = "手动切换模型到指定级别")
    public BaseResponse<String> switchModel(@PathVariable String modelType, @RequestParam String targetLevel) {
        try {
            String beanName = getBeanNameByModelType(modelType);
            Object model = getModelByPrototype(beanName);

            MultiFallbackStreamingChatModel.ModelLevel target = parseModelLevel(targetLevel);
            if (target == null) {
                return (BaseResponse<String>) ResultUtils.error(400, "无效的目标级别: " + targetLevel);
            }

            if (model instanceof MultiFallbackStreamingChatModel) {
                MultiFallbackStreamingChatModel multiModel = (MultiFallbackStreamingChatModel) model;
                boolean success = multiModel.switchToLevel(target);
                if (success) {
                    log.info("已手动切换 {} 到 {}", modelType, target.getDesc());
                    return ResultUtils.success("已切换 " + modelType + " 到 " + target.getDesc());
                } else {
                    return (BaseResponse<String>) ResultUtils.error(400, "切换失败，目标模型未配置");
                }
            }

            return (BaseResponse<String>) ResultUtils.error(400, "模型类型 " + modelType + " 不支持手动切换");

        } catch (Exception e) {
            log.error("切换模型 {} 失败", modelType, e);
            return (BaseResponse<String>) ResultUtils.error(500, "切换模型失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的模型级别
     */
    @GetMapping("/levels")
    @Operation(summary = "获取模型级别", description = "获取所有可用的模型级别")
    public BaseResponse<Map<String, String>> getAvailableLevels() {
        Map<String, String> levels = new HashMap<>();
        for (MultiFallbackStreamingChatModel.ModelLevel level : MultiFallbackStreamingChatModel.ModelLevel.values()) {
            levels.put(level.name().toLowerCase(), level.getDesc());
        }
        return ResultUtils.success(levels);
    }

    /**
     * 根据模型类型获取Bean名称
     */
    private String getBeanNameByModelType(String modelType) {
        return switch (modelType.toLowerCase()) {
            case "chat-model", "chat" -> "multiFallbackOpenAiChatModel";
            case "streaming-chat-model", "streaming" -> "streamingChatModelPrototype";
            case "reasoning-streaming-chat-model", "reasoning" -> "reasoningStreamingChatModelPrototype";
            // routing-chat-model不支持三级备用，但保留API接口兼容性
            case "routing-chat-model", "routing" -> "routingChatModelPrototype";
            default -> throw new IllegalArgumentException("未知的模型类型: " + modelType);
        };
    }

    /**
     * 解析模型级别
     */
    private MultiFallbackStreamingChatModel.ModelLevel parseModelLevel(String level) {
        try {
            return MultiFallbackStreamingChatModel.ModelLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 通过名称获取模型实例（支持原型和单例）
     */
    @SuppressWarnings("unchecked")
    private <T> T getModelByName(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    /**
     * 通过原型获取模型实例
     */
    @SuppressWarnings("unchecked")
    private <T> T getModelByPrototype(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }
}