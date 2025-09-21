package com.ran.aicode.core.hander;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ran.aicode.ai.model.message.*;
import com.ran.aicode.ai.tools.BaseTool;
import com.ran.aicode.ai.tools.ToolManager;
import com.ran.aicode.model.entity.User;
import com.ran.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.ran.aicode.model.enums.ChatHistoryStatusEnum;
import com.ran.aicode.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    // 添加一个 Map 来累积每个工具调用的参数
    private final Map<String, StringBuilder> toolArgumentsAccumulator = new ConcurrentHashMap<>();

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    //判断消息状态
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId(), ChatHistoryStatusEnum.NORMAL.getValue(),null);
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId(),ChatHistoryStatusEnum.AI_INTERRUPTED.getValue(),null);
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 先做健壮性判断：有些模型会直接返回纯文本/围栏 JSON
        if (StrUtil.isBlank(chunk)) {
            return "";
        }
        String trimmed = chunk.trim();
        // 如果不是以 { 开头，视为普通文本增量，直接回显，避免 JSON 解析异常
        if (!trimmed.startsWith("{")) {
            chatHistoryStringBuilder.append(trimmed);
            return trimmed;
        }

        // 解析 JSON
        StreamMessage streamMessage;
        try {
            streamMessage = JSONUtil.toBean(trimmed, StreamMessage.class);
        } catch (Exception e) {
            log.warn("JSON 流块解析失败，按文本回退处理，chunk={}", StrUtil.maxLength(trimmed, 200));
            chatHistoryStringBuilder.append(trimmed);
            return trimmed;
        }
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolId = toolExecutedMessage.getId();
                String toolName = toolExecutedMessage.getName();
                String argsStr = toolExecutedMessage.getArguments();
                
                log.info("收到工具执行消息: toolId={}, toolName={}, argsStr={}", toolId, toolName, StrUtil.maxLength(argsStr, 100));
                
                // 累积参数
                StringBuilder argsBuilder = toolArgumentsAccumulator.computeIfAbsent(toolId, k -> new StringBuilder());
                argsBuilder.append(argsStr);
                
                // 检查参数是否完整
                String accumulatedArgs = argsBuilder.toString();
                log.info("累积参数: toolId={}, 当前长度={}, 内容预览={}", toolId, accumulatedArgs.length(), StrUtil.maxLength(accumulatedArgs, 200));
                
                if (isArgumentsComplete(accumulatedArgs)) {
                    log.info("参数完整，开始执行工具调用: toolId={}, toolName={}", toolId, toolName);
                    // 参数完整，执行工具调用
                    String cleanedArgs = sanitizeJsonString(accumulatedArgs);
                    JSONObject jsonObject;
                    try {
                        jsonObject = JSONUtil.parseObj(cleanedArgs);
                        log.info("工具参数解析成功: {}", jsonObject);
                    } catch (Exception ex) {
                        log.warn("工具参数解析失败，使用空对象回退。name={}, args={}",
                                toolName, StrUtil.maxLength(accumulatedArgs, 200));
                        jsonObject = new JSONObject();
                    }
                    
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    String result = tool.generateToolExecutedResult(jsonObject);
                    String output = String.format("\n\n%s\n\n", result);
                    chatHistoryStringBuilder.append(output);
                    
                    log.info("工具执行完成: toolId={}, result={}", toolId, StrUtil.maxLength(result, 100));
                    
                    // 清理已完成的工具调用
                    toolArgumentsAccumulator.remove(toolId);
                    
                    return output;
                } else {
                    // 参数还不完整，不返回内容，继续累积
                    log.info("工具调用参数累积中，toolId={}, 当前长度={}", toolId, accumulatedArgs.length());
                    return "";
                }
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }

    /**
     * 判断工具调用参数是否完整
     * 简单判断：JSON 字符串是否以 } 结尾且括号平衡
     */
    private boolean isArgumentsComplete(String args) {
        if (StrUtil.isBlank(args)) {
            log.debug("参数为空，不完整");
            return false;
        }
        
        String trimmed = args.trim();
        log.debug("检查参数完整性: 长度={}, 开始={}, 结束={}", trimmed.length(), 
                trimmed.length() > 0 ? trimmed.charAt(0) : 'N', 
                trimmed.length() > 0 ? trimmed.charAt(trimmed.length() - 1) : 'N');
        
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            log.debug("参数不以{{}}包围，不完整");
            return false;
        }
        
        // 简单的括号平衡检查
        int braceCount = 0;
        boolean inString = false;
        char prevChar = 0;
        
        for (char c : trimmed.toCharArray()) {
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                }
            }
            prevChar = c;
        }
        
        boolean complete = braceCount == 0;
        log.debug("括号平衡检查: braceCount={}, complete={}", braceCount, complete);
        return complete;
    }

    /**
     * 去除常见的 Markdown 代码围栏与无关前后缀，返回尽可能可被 JSON 解析的字符串
     */
    private String sanitizeJsonString(String original) {
        if (original == null) {
            return "{}";
        }
        String s = original.trim();
        // 移除 ```json 或 ``` 包裹
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```json\\s*", "");
            s = s.replaceFirst("^```\n?", "");
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence).trim();
            }
        }
        // 去掉前后多余字符直到以 { 开始、以 } 结束（尽量温和，不做复杂平衡）
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            s = s.substring(start, end + 1).trim();
        }
        return s;
    }
}
