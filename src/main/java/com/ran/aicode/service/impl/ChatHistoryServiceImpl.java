package com.ran.aicode.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.ran.aicode.exception.ErrorCode;
import com.ran.aicode.exception.ThrowUtils;
import com.ran.aicode.mapper.ChatHistoryMapper;
import com.ran.aicode.model.dto.ChatHistoryQueryRequest;
import com.ran.aicode.model.entity.ChatHistory;
import com.ran.aicode.model.entity.User;
import com.ran.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.ran.aicode.model.enums.ChatHistoryTypeEnum;
import com.ran.aicode.model.vo.ChatHistoryVO;
import com.ran.aicode.service.ChatHistoryService;
import com.ran.aicode.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.mybatisflex.core.paginate.Page;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对话历史 服务层实现。
 *
 * @author <a>ran</a>
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    private UserService userService;

    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    @Override
    public Page<ChatHistoryVO> listAppChatHistoryVOByPage(Long appId, int pageSize,
                                                          LocalDateTime lastCreateTime
    ) {
        // 先获取原始的ChatHistory分页数据
        Page<ChatHistory> chatHistoryPage = listAppChatHistoryByPage(appId, pageSize, lastCreateTime);

        // 转换为ChatHistoryVO
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>();
        chatHistoryVOPage.setPageNumber(chatHistoryPage.getPageNumber());
        chatHistoryVOPage.setPageSize(chatHistoryPage.getPageSize());
        chatHistoryVOPage.setTotalRow(chatHistoryPage.getTotalRow());
        chatHistoryVOPage.setTotalPage(chatHistoryPage.getTotalPage());

        // 转换记录
        List<ChatHistoryVO> voList = chatHistoryPage.getRecords().stream()
                .map(this::convertToVO)
                .toList();
        chatHistoryVOPage.setRecords(voList);

        return chatHistoryVOPage;
    }

    /**
     * 将ChatHistory转换为ChatHistoryVO
     */
    private ChatHistoryVO convertToVO(ChatHistory chatHistory) {
        ChatHistoryVO vo = ChatHistoryVO.builder()
                .message(chatHistory.getMessage())
                .messageType(chatHistory.getMessageType())
                .appId(chatHistory.getAppId())
                .createTime(chatHistory.getCreateTime())
                .status(chatHistory.getStatus())
                .type(chatHistory.getType())
                .build();

        // 获取用户昵称
        if (chatHistory.getUserId() != null) {
            User user = userService.getById(chatHistory.getUserId());
            if (user != null) {
                vo.setUserName(user.getUserName());
            }
        }

        return vo;
    }

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId, Integer status, List<String> image) {

        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(message.length() <= 0, ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);

        // 记录消息长度，监控长消息
        int messageLength = message.length();
        int messageBytes = message.getBytes().length;
        if (messageLength > 50000) {
            log.warn("长消息警告: appId={}, messageType={}, 字符数={}, 字节数={}",
                    appId, messageType, messageLength, messageBytes);
        } else if (messageLength > 20000) {
            log.info("中等长度消息: appId={}, messageType={}, 字符数={}, 字节数={}",
                    appId, messageType, messageLength, messageBytes);
        }

        //相同消息生成唯一键,表示同一条消息
        String jsonStr = appId + message + messageType + userId + status;
        String onlyId = DigestUtil.md5Hex(jsonStr);
        List<ChatHistory> chatHistory = new ArrayList<>();
        chatHistory.add(ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .status(status)
                .type(ChatHistoryTypeEnum.TEXT.getValue())
                .onlyId(onlyId)
                .build());
        if (image != null && image.size() > 0) {
            for (String ms : image) {
                chatHistory.add(ChatHistory.builder()
                        .appId(appId)
                        .message(ms)
                        .messageType(messageType)
                        .userId(userId)
                        .status(status)
                        .onlyId(onlyId)
                        .type(ChatHistoryTypeEnum.IMAGE.getValue())
                        .build());
            }
        }

        return this.saveBatch(chatHistory);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    @Override
    public Page<ChatHistoryVO> listAllChatHistoryVOByPageForAdmin(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();

        // 查询数据
        QueryWrapper queryWrapper = this.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> chatHistoryPage = this.page(Page.of(pageNum, pageSize), queryWrapper);

        // 转换为ChatHistoryVO
        Page<ChatHistoryVO> chatHistoryVOPage = new Page<>();
        chatHistoryVOPage.setPageNumber(chatHistoryPage.getPageNumber());
        chatHistoryVOPage.setPageSize(chatHistoryPage.getPageSize());
        chatHistoryVOPage.setTotalRow(chatHistoryPage.getTotalRow());
        chatHistoryVOPage.setTotalPage(chatHistoryPage.getTotalPage());

        // 转换记录
        List<ChatHistoryVO> voList = chatHistoryPage.getRecords().stream()
                .map(this::convertToVO)
                .toList();
        chatHistoryVOPage.setRecords(voList);

        return chatHistoryVOPage;
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 查询历史记录
            List<ChatHistory> historyList = queryChatHistory(appId, maxCount);
            if (CollUtil.isEmpty(historyList)) {
                log.info("应用 {} 没有历史对话记录", appId);
                return 0;
            }
            // 清理历史缓存，防止重复加载
            chatMemory.clear();
            // 按 onlyId 分组并处理
            int loadedCount = processGroupedHistory(historyList, chatMemory);

            // 验证并修复消息顺序（确保符合OpenAI API规范）
            validateAndFixMessageOrder(chatMemory);

            // === 诊断日志：打印加载的消息顺序 ===
            if (loadedCount > 0) {
                List<ChatMessage> messages = chatMemory.messages();
                log.info("=== 开始诊断历史消息 appId: {} ===", appId);
                log.info("历史消息总数: {}", messages.size());

                for (int i = 0; i < messages.size(); i++) {
                    ChatMessage msg = messages.get(i);
                    // 根据消息类型获取文本内容
                    String preview = "[类型:" + msg.type() + "]";
                    if (msg instanceof UserMessage) {
                        UserMessage userMsg = (UserMessage) msg;
                        String text = userMsg.singleText();
                        preview = text != null ? text.substring(0, Math.min(100, text.length())) : "[无内容]";
                    } else if (msg instanceof AiMessage) {
                        AiMessage aiMsg = (AiMessage) msg;
                        String text = aiMsg.text();
                        preview = text != null ? text.substring(0, Math.min(100, text.length())) : "[无内容]";
                    }
                    log.info("Message[{}]: type={}, preview={}", i, msg.type(), preview);
                }

                // 检查最后一条消息类型
                ChatMessage lastMsg = messages.get(messages.size() - 1);
                if (lastMsg.type() != ChatMessageType.USER) {
                    log.error("❌ 消息顺序异常：最后一条消息不是USER类型，而是 {}", lastMsg.type());
                } else {
                    log.info("✅ 消息顺序正常：最后一条消息是USER类型");
                }

                log.info("=== 诊断结束 ===");
            }

            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }
    
    /**
     * 查询聊天历史记录
     */
    private List<ChatHistory> queryChatHistory(Long appId, int maxCount) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatHistory::getAppId, appId)
                .orderBy(ChatHistory::getCreateTime, false)
                .limit(1, maxCount);
        
        List<ChatHistory> historyList = this.list(queryWrapper);
        // 反转列表，确保按时间正序（老的在前，新的在后）
        return historyList.reversed();
    }
    
    /**
     * 处理分组后的历史记录
     */
    private int processGroupedHistory(List<ChatHistory> historyList, MessageWindowChatMemory chatMemory) {
        // 按 onlyId 分组，过滤掉空值
        Map<String, List<ChatHistory>> groupedHistory = historyList.stream()
                .filter(history -> StrUtil.isNotBlank(history.getOnlyId()))
                .collect(Collectors.groupingBy(ChatHistory::getOnlyId));
        int loadedCount = 0;
        for (List<ChatHistory> group : groupedHistory.values()) {
            if (CollUtil.isEmpty(group)) {
                continue;
            }
            String mergedMessage = mergeGroupMessages(group);
            if (StrUtil.isNotBlank(mergedMessage)) {
                boolean added = addMessageToMemory(group.get(0).getMessageType(), mergedMessage, chatMemory);
                if (added) {
                    loadedCount++;
                }
            }
        }
        return loadedCount;
    }
    
    /**
     * 合并分组内的消息
     */
    private String mergeGroupMessages(List<ChatHistory> group) {
        // 分离文本和图片消息
        String textMessage = group.stream()
                .filter(history -> ChatHistoryTypeEnum.TEXT.getValue().equals(history.getType()))
                .map(ChatHistory::getMessage)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        
        List<String> imageMessages = group.stream()
                .filter(history -> ChatHistoryTypeEnum.IMAGE.getValue().equals(history.getType()))
                .map(ChatHistory::getMessage)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        
        // 构建合并后的消息内容
        StringBuilder mergedMessage = new StringBuilder();
        
        // 添加文本消息
        if (StrUtil.isNotBlank(textMessage)) {
            mergedMessage.append(textMessage);
        }
        
        // 添加图片消息
        for (String imageMsg : imageMessages) {
            if (mergedMessage.length() > 0) {
                mergedMessage.append("\n");
            }
            mergedMessage.append(imageMsg);
        }
        
        return mergedMessage.toString();
    }
    
    /**
     * 将消息添加到聊天记忆中
     */
    private boolean addMessageToMemory(String messageType, String message, MessageWindowChatMemory chatMemory) {
        if (StrUtil.isBlank(messageType) || StrUtil.isBlank(message)) {
            return false;
        }
        
        try {
            if (ChatHistoryMessageTypeEnum.USER.getValue().equals(messageType)) {
                chatMemory.add(UserMessage.from(message));
                return true;
            } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(messageType)) {
                chatMemory.add(AiMessage.from(message));
                return true;
            } else {
                log.warn("不支持的消息类型: {}", messageType);
                return false;
            }
        } catch (Exception e) {
            log.error("添加消息到聊天记忆失败，messageType: {}, message: {}, error: {}", 
                    messageType, message, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 验证并修复消息顺序，确保符合OpenAI API规范
     *
     * 规则：
     * 1. 最后一条消息必须是USER类型（因为接下来会添加AI响应）
     * 2. USER和AI消息必须严格交替
     *
     * @param chatMemory 聊天记忆对象
     */
    private void validateAndFixMessageOrder(MessageWindowChatMemory chatMemory) {
        List<ChatMessage> messages = chatMemory.messages();

        if (messages.isEmpty()) {
            log.debug("消息列表为空，无需验证顺序");
            return;
        }

        int originalSize = messages.size();

        // 规则1：最后一条消息必须是USER类型
        // 如果不是，移除末尾的非USER消息，直到最后一条是USER或列表为空
        while (!messages.isEmpty()) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.type() == ChatMessageType.USER) {
                break; // 找到USER消息，停止移除
            }

            // 获取消息预览内容
            String preview = "[无内容]";
            if (lastMessage instanceof UserMessage) {
                UserMessage userMsg = (UserMessage) lastMessage;
                String text = userMsg.singleText();
                preview = text != null ? text.substring(0, Math.min(50, text.length())) : "[无内容]";
            } else if (lastMessage instanceof AiMessage) {
                AiMessage aiMsg = (AiMessage) lastMessage;
                String text = aiMsg.text();
                preview = text != null ? text.substring(0, Math.min(50, text.length())) : "[无内容]";
            }

            log.warn("消息顺序修复：移除末尾的非USER消息，type={}, content preview={}",
                lastMessage.type(), preview);

            messages.remove(messages.size() - 1);
        }

        if (messages.isEmpty()) {
            log.warn("消息顺序修复后列表为空，所有消息都不是USER类型");
            return;
        }

        // 规则2：验证USER-AI交替顺序（仅警告，不修复）
        for (int i = 0; i < messages.size() - 1; i++) {
            ChatMessage current = messages.get(i);
            ChatMessage next = messages.get(i + 1);

            boolean isValidSequence =
                (current.type() == ChatMessageType.USER &&
                 next.type() == ChatMessageType.AI) ||
                (current.type() == ChatMessageType.AI &&
                 next.type() == ChatMessageType.USER);

            if (!isValidSequence) {
                log.error("❌ 消息交替顺序异常：Message[{}]={}, Message[{}]={}",
                    i, current.type(), i + 1, next.type());
            }
        }

        int fixedSize = messages.size();
        if (originalSize != fixedSize) {
            log.info("消息顺序修复完成：原始数量={}, 修复后数量={}, 移除了{}条消息",
                originalSize, fixedSize, originalSize - fixedSize);
        } else {
            log.debug("消息顺序验证通过，无需修复");
        }
    }

    @Override
    public boolean updateChatHistoryStatus(Long appId, Long userId, Integer status) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(status == null, ErrorCode.PARAMS_ERROR, "状态不能为空");

        try {
            // 构建更新条件：根据appId和userId更新最新的AI消息状态
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .eq(ChatHistory::getUserId, userId)
                    .eq(ChatHistory::getMessageType, ChatHistoryMessageTypeEnum.AI.getValue())
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1);

            // 更新状态
            ChatHistory updateEntity = new ChatHistory();
            updateEntity.setStatus(status);

            boolean result = this.update(updateEntity, queryWrapper);
            log.info("更新聊天记录状态: appId={}, userId={}, status={}, result={}", appId, userId, status, result);
            return result;
        } catch (Exception e) {
            log.error("更新聊天记录状态失败: appId={}, userId={}, status={}", appId, userId, status, e);
            return false;
        }
    }

}
