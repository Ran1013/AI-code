package com.ran.aicode.service;

import java.util.Map;

/**
 * 通用邮件服务接口
 */
public interface TencentEmailService {

    /**
     * 发送模板邮件
     *
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param templateId 模板 ID
     * @param templateData 模板变量
     */
    void sendTemplateEmail(String to, String subject, Integer templateId, Map<String, Object> templateData);

}

