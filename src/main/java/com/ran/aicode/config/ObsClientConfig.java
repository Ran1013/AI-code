package com.ran.aicode.config;

import com.obs.services.ObsClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 华为云OBS配置类
 */
@Configuration
@ConfigurationProperties(prefix = "obs.client")
@Data
public class ObsClientConfig {

    /**
     * 域名
     */
    private String host;

    /**
     * accessKey
     */
    private String accessKey;

    /**
     * secretKey
     */
    private String secretKey;

    /**
     * endpoint
     */
    private String endpoint;

    /**
     * 桶名
     */
    private String bucket;

    @Bean
    public ObsClient obsClient() {
        return new ObsClient(accessKey, secretKey, endpoint);
    }
}

