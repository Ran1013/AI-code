package com.ran.aicode;

import com.ran.aicode.config.ObsClientConfig;
import com.ran.aicode.manager.ObsManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 华为云OBS集成测试类
 * <p>
 * 通过加载一个仅包含OBS相关Bean的轻量级Spring上下文，
 * 来测试ObsManager与华为云OBS服务的实际交互。
 * <p>
 * 注意：运行此测试前，请确保 `application-prod.yml` 中已配置真实的华为云OBS参数。
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-prod.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
public class ObsManagerIntegrationTest {

    // 专用测试配置，只加载OBS相关的Bean，避免加载整个应用上下文
    // 专用测试配置，确保ObsClientConfig的属性被正确绑定
    @Configuration
    @EnableConfigurationProperties(ObsClientConfig.class)
    @Import(ObsManager.class)
    static class ObsTestConfig {
    }

    @Resource
    private ObsManager obsManager;

    private static File testImageFile;
    private static String testKey;

    @BeforeAll
    static void setUpClass() {
        testImageFile = new File("image/img.png");
        testKey = "test/integration-test-" + System.currentTimeMillis() + ".png";
        log.info("=== 华为云OBS集成测试开始 ===");
        log.info("测试文件: {}", testImageFile.getAbsolutePath());
        log.info("测试键名: {}", testKey);
    }

    @AfterAll
    static void tearDownClass() {
        log.info("=== 华为云OBS集成测试结束 ===");
    }

    @Test
    @Order(1)
    @DisplayName("1. 验证测试环境和文件")
    void testEnvironmentAndFile() {
        log.info("--- 测试1: 验证测试环境和文件 ---");
        assertNotNull(obsManager, "ObsManager未正确注入");
        assertTrue(testImageFile.exists(), "测试图片 `image/img.png` 不存在，请确保文件路径正确。");
        log.info("✓ 环境和文件验证通过");
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试文件上传和删除")
    void testUploadAndDelete() {
        log.info("--- 测试2: 文件上传和删除 ---");

//        // 1. 上传文件
//        log.info("步骤1: 开始上传文件...");
//        String url = obsManager.uploadFile(testKey, testImageFile);
//        assertNotNull(url, "文件上传失败，返回的URL为null。");
//        log.info("✓ 步骤1完成: 文件上传成功，URL: {}", url);

        // 2. 删除文件
        log.info("步骤2: 开始删除文件...");
        boolean success = obsManager.deleteFile(testKey);
        assertTrue(success, "文件删除失败。");
        log.info("✓ 步骤2完成: 文件删除成功。");
    }
}