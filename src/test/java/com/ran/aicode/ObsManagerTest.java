package com.ran.aicode;

import com.ran.aicode.config.ObsClientConfig;
import com.ran.aicode.manager.ObsManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OBS管理器集成测试
 * <p>
 * 通过加载一个仅包含OBS相关Bean的轻量级Spring上下文，
 * 来测试ObsManager与华为云OBS服务的实际交互。
 * <p>
 * 注意：运行此测试前，请确保 `application-prod.yml` 中已配置好正确的OBS参数。
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-prod.yml")
@Slf4j
public class ObsManagerTest {

    // 专用测试配置，只加载OBS相关的Bean，避免加载整个应用上下文
    // 专用测试配置，确保ObsClientConfig的属性被正确绑定
    @Configuration
    @EnableConfigurationProperties(ObsClientConfig.class)
    @Import(ObsManager.class)
    static class ObsTestConfig {
    }

    @Resource
    private ObsManager obsManager;

    @Test
    @DisplayName("测试文件上传和删除的完整流程")
    void testUploadAndDelete() {
        String key = "test/obs-manager-test-" + System.currentTimeMillis() + ".png";
        File testImageFile = new File("image/img.png");

        // 验证测试文件存在
        assertTrue(testImageFile.exists(), "测试图片 `image/img.png` 不存在，请确保文件路径正确。");

        // 1. 测试上传
        log.info("开始测试文件上传，文件键: {}", key);
        String url = obsManager.uploadFile(key, testImageFile);
        assertNotNull(url, "文件上传失败，返回的URL为null。");
        log.info("文件上传成功，访问URL: {}", url);

        // 2. 测试删除
        log.info("开始测试文件删除，文件键: {}", key);
        boolean success = obsManager.deleteFile(key);
        assertTrue(success, "文件删除失败。");
        log.info("文件删除成功。");
    }
}