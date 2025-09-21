package com.ran.aicode.utils;

import com.ran.aicode.config.TencentCloudConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 邮件配置验证工具类
 */
@Component
@Slf4j
public class EmailConfigValidator {

    /**
     * 验证腾讯云邮件配置是否完整
     */
    public boolean validateTencentCloudConfig(TencentCloudConfig config) {
        if (config == null) {
            log.error("腾讯云配置为空");
            return false;
        }
        
        if (config.getSecretId() == null || config.getSecretId().trim().isEmpty()) {
            log.error("腾讯云SecretId配置为空");
            return false;
        }
        
        if (config.getSecretKey() == null || config.getSecretKey().trim().isEmpty()) {
            log.error("腾讯云SecretKey配置为空");
            return false;
        }
        
        if (config.getSes() == null) {
            log.error("腾讯云SES配置为空");
            return false;
        }
        
        if (config.getSes().getRegion() == null || config.getSes().getRegion().trim().isEmpty()) {
            log.error("腾讯云SES区域配置为空");
            return false;
        }
        
        if (config.getSes().getTemplateId() == null) {
            log.error("腾讯云SES模板ID配置为空");
            return false;
        }
        
        log.info("腾讯云邮件配置验证通过");
        return true;
    }
}
