package com.ran.aicode.service.impl;

import cn.hutool.json.JSONUtil;
import com.ran.aicode.config.TencentCloudConfig;
import com.ran.aicode.exception.BusinessException;
import com.ran.aicode.exception.ErrorCode;
import com.ran.aicode.service.TencentEmailService;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.Template;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 腾讯云邮件服务实现 (根据官方源码优化)
 */
@Service
@Slf4j
public class TencentEmailServiceImpl implements TencentEmailService {

    @Resource
    private TencentCloudConfig tencentCloudConfig;

    @Value("${spring.mail.from}")
    private String fromEmail;

    @Override
    public void sendTemplateEmail(String to, String subject, Integer templateId, Map<String, Object> templateData) {
        try {
            Credential cred = new Credential(tencentCloudConfig.getSecretId(), tencentCloudConfig.getSecretKey());
            SesClient client = new SesClient(cred, tencentCloudConfig.getSes().getRegion());

            SendEmailRequest req = new SendEmailRequest();
            req.setFromEmailAddress(fromEmail);
            req.setDestination(new String[]{to});
            req.setSubject(subject);

            // SDK v3.1.1299 设置模板的正确方式
            Template template = new Template();
            template.setTemplateID(Long.valueOf(templateId));
            template.setTemplateData(JSONUtil.toJsonStr(templateData));
            req.setTemplate(template);

            client.SendEmail(req);
            log.info("腾讯云模板邮件发送成功，收件人: {}", to);

        } catch (TencentCloudSDKException e) {
            log.error("腾讯云模板邮件发送失败，收件人: {}, 错误: {}", to, e.toString());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码发送失败，请检查配置或稍后再试");
        }
    }
}
