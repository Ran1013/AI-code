package com.ran.aicode.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云相关配置
 */
@Configuration
@ConfigurationProperties(prefix = "tencent")
@Data
public class TencentCloudConfig {

    private String secretId;
    private String secretKey;
    private Ses ses;

    @Data
    public static class Ses {
        private String region;
        private Integer templateId; // 模板ID通常是数字
    }
}

