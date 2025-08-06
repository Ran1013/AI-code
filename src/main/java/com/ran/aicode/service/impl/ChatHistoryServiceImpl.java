package com.ran.aicode.service.impl;


import com.ran.aicode.mapper.ChatHistoryMapper;
import com.ran.aicode.model.entity.ChatHistory;
import com.ran.aicode.service.ChatHistoryService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 对话历史 服务层实现。
 *
 * @author <a>ran</a>
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>  implements ChatHistoryService {

}
