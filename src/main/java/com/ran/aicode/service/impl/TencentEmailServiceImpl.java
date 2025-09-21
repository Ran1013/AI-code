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

    @Resource
    private com.ran.aicode.utils.EmailConfigValidator emailConfigValidator;

    @jakarta.annotation.PostConstruct
    public void init() {
        // 启动时验证配置
        if (!emailConfigValidator.validateTencentCloudConfig(tencentCloudConfig)) {
            log.warn("腾讯云邮件配置不完整，邮件功能可能无法正常工作");
        }
        if (fromEmail == null || fromEmail.trim().isEmpty()) {
            log.warn("发件人邮箱配置为空，邮件功能可能无法正常工作");
        }
    }

    @Override
    public void sendTemplateEmail(String to, String subject, Integer templateId, Map<String, Object> templateData) {
        // 参数验证
        if (to == null || to.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "收件人邮箱不能为空");
        }
        if (templateId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮件模板ID不能为空");
        }
        
        // 配置验证
        if (tencentCloudConfig.getSecretId() == null || tencentCloudConfig.getSecretKey() == null) {
            log.error("腾讯云配置缺失，SecretId或SecretKey为空");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "邮件服务配置错误，请联系管理员");
        }
        
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
            log.error("腾讯云模板邮件发送失败，收件人: {}, 错误代码: {}, 错误信息: {}", to, e.getErrorCode(), e.getMessage());
            // 根据具体错误提供更友好的提示
            String errorMsg = "验证码发送失败";
            if (e.getErrorCode() != null) {
                switch (e.getErrorCode()) {
                    case "InvalidParameter.TemplateNotExist":
                        errorMsg = "邮件模板配置错误，请联系管理员";
                        break;
                    case "InvalidParameter.EmailAddress":
                        errorMsg = "邮箱地址格式不正确";
                        break;
                    case "LimitExceeded.DailyQuota":
                        errorMsg = "今日邮件发送量已达上限，请明日再试";
                        break;
                    default:
                        errorMsg = "验证码发送失败，请稍后再试";
                }
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMsg);
        } catch (Exception e) {
            log.error("邮件发送异常，收件人: {}, 异常: {}", to, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统异常，请稍后再试");
        }
    }
}
