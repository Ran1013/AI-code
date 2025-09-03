package com.ran.aicode.websocket.model;

import com.ran.aicode.model.vo.UserVO;
import lombok.Data;

@Data
public class AppResponseMessage {
    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 格式
     */
    private String type;
    /**
     * 动作
     */
    private String editAction;

    /**
     * 用户信息
     */
    private UserVO user;
}
