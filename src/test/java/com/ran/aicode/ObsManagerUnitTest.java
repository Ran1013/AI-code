package com.ran.aicode;

import com.obs.services.ObsClient;
import com.obs.services.model.PutObjectResult;
import com.ran.aicode.config.ObsClientConfig;
import com.ran.aicode.manager.ObsManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ObsManager单元测试类
 * 使用Mock对象测试ObsManager的业务逻辑，不依赖实际的华为云OBS服务
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
public class ObsManagerUnitTest {

    @Mock
    private ObsClientConfig obsClientConfig;

    @Mock
    private ObsClient obsClient;

    @InjectMocks
    private ObsManager obsManager;

    private File testImageFile;
    private final String testBucket = "test-bucket";
    private final String testHost = "https://test-bucket.obs.cn-north-4.myhuaweicloud.com";
    private final String testKey = "test/unit-test.png";

    @BeforeEach
    void setUp() {
        // 准备测试文件
        testImageFile = new File("image/img.png");
        
        // 配置Mock对象的行为
        when(obsClientConfig.getBucket()).thenReturn(testBucket);
        when(obsClientConfig.getHost()).thenReturn(testHost);
        
        log.info("单元测试环境准备完成");
    }

    @Test
    @DisplayName("测试文件上传成功场景")
    void testUploadFileSuccess() {
        log.info("--- 测试文件上传成功场景 ---");
        
        // 模拟成功的上传结果
        PutObjectResult mockResult = mock(PutObjectResult.class);
        when(obsClient.putObject(any())).thenReturn(mockResult);
        
        // 执行上传
        String result = obsManager.uploadFile(testKey, testImageFile);
        
        // 验证结果
        assertNotNull(result, "上传成功应该返回URL");
        assertEquals(testHost + "/" + testKey, result, "返回的URL格式不正确");
        
        // 验证Mock调用
        verify(obsClient, times(1)).putObject(any());
        verify(obsClientConfig, times(1)).getBucket();
        verify(obsClientConfig, times(1)).getHost();
        
        log.info("✓ 文件上传成功测试通过 - URL: {}", result);
    }

    @Test
    @DisplayName("测试文件上传失败场景")
    void testUploadFileFailure() {
        log.info("--- 测试文件上传失败场景 ---");
        
        // 模拟上传异常
        when(obsClient.putObject(any())).thenThrow(new RuntimeException("模拟上传失败"));
        
        // 执行上传
        String result = obsManager.uploadFile(testKey, testImageFile);
        
        // 验证结果
        assertNull(result, "上传失败应该返回null");
        
        // 验证Mock调用
        verify(obsClient, times(1)).putObject(any());
        
        log.info("✓ 文件上传失败测试通过");
    }

    @Test
    @DisplayName("测试文件删除成功场景")
    void testDeleteFileSuccess() {
        log.info("--- 测试文件删除成功场景 ---");
        
        // 模拟删除成功（不抛异常）
        doNothing().when(obsClient).deleteObject(testBucket, testKey);
        
        // 执行删除
        boolean result = obsManager.deleteFile(testKey);
        
        // 验证结果
        assertTrue(result, "删除成功应该返回true");
        
        // 验证Mock调用
        verify(obsClient, times(1)).deleteObject(testBucket, testKey);
        verify(obsClientConfig, times(1)).getBucket();
        
        log.info("✓ 文件删除成功测试通过");
    }

    @Test
    @DisplayName("测试文件删除失败场景")
    void testDeleteFileFailure() {
        log.info("--- 测试文件删除失败场景 ---");
        
        // 模拟删除异常
        doThrow(new RuntimeException("模拟删除失败")).when(obsClient).deleteObject(testBucket, testKey);
        
        // 执行删除
        boolean result = obsManager.deleteFile(testKey);
        
        // 验证结果
        assertFalse(result, "删除失败应该返回false");
        
        // 验证Mock调用
        verify(obsClient, times(1)).deleteObject(testBucket, testKey);
        
        log.info("✓ 文件删除失败测试通过");
    }

    @Test
    @DisplayName("测试URL键提取功能")
    void testExtractKeyFromUrl() {
        log.info("--- 测试URL键提取功能 ---");
        
        // 测试正常URL
        String validUrl = testHost + "/" + testKey;
        String extractedKey = obsManager.extractKeyFromUrl(validUrl);
        assertEquals(testKey, extractedKey, "应该正确提取键名");
        log.info("✓ 正常URL键提取测试通过 - 键: {}", extractedKey);
        
        // 测试无效host的URL
        String invalidHostUrl = "https://invalid-host.com/" + testKey;
        String invalidResult = obsManager.extractKeyFromUrl(invalidHostUrl);
        assertNull(invalidResult, "无效host应该返回null");
        log.info("✓ 无效host URL测试通过");
        
        // 测试null URL
        String nullResult = obsManager.extractKeyFromUrl(null);
        assertNull(nullResult, "null URL应该返回null");
        log.info("✓ null URL测试通过");
        
        // 测试空字符串URL
        String emptyResult = obsManager.extractKeyFromUrl("");
        assertNull(emptyResult, "空字符串URL应该返回null");
        log.info("✓ 空字符串URL测试通过");
        
        // 验证Mock调用
        verify(obsClientConfig, times(4)).getHost();
    }

    @Test
    @DisplayName("测试URL键提取边界情况")
    void testExtractKeyFromUrlEdgeCases() {
        log.info("--- 测试URL键提取边界情况 ---");
        
        // 测试只有host没有键的URL
        String hostOnlyUrl = testHost;
        String hostOnlyResult = obsManager.extractKeyFromUrl(hostOnlyUrl);
        assertEquals("", hostOnlyResult, "只有host的URL应该返回空字符串");
        log.info("✓ 只有host的URL测试通过");
        
        // 测试host后只有斜杠的URL
        String slashOnlyUrl = testHost + "/";
        String slashOnlyResult = obsManager.extractKeyFromUrl(slashOnlyUrl);
        assertEquals("", slashOnlyResult, "host后只有斜杠应该返回空字符串");
        log.info("✓ host后只有斜杠的URL测试通过");
        
        // 测试包含多级路径的键
        String multiLevelKey = "folder1/folder2/file.png";
        String multiLevelUrl = testHost + "/" + multiLevelKey;
        String multiLevelResult = obsManager.extractKeyFromUrl(multiLevelUrl);
        assertEquals(multiLevelKey, multiLevelResult, "应该正确提取多级路径键");
        log.info("✓ 多级路径键提取测试通过 - 键: {}", multiLevelResult);
    }

    @Test
    @DisplayName("测试putObject方法")
    void testPutObject() {
        log.info("--- 测试putObject方法 ---");
        
        // 模拟成功的上传结果
        PutObjectResult mockResult = mock(PutObjectResult.class);
        when(obsClient.putObject(any())).thenReturn(mockResult);
        
        // 执行putObject
        PutObjectResult result = obsManager.putObject(testKey, testImageFile);
        
        // 验证结果
        assertNotNull(result, "putObject应该返回结果对象");
        assertEquals(mockResult, result, "应该返回Mock的结果对象");
        
        // 验证Mock调用
        verify(obsClient, times(1)).putObject(any());
        verify(obsClientConfig, times(1)).getBucket();
        
        log.info("✓ putObject方法测试通过");
    }

    @Test
    @DisplayName("测试完整业务流程")
    void testCompleteBusinessFlow() {
        log.info("--- 测试完整业务流程 ---");
        
        // 模拟上传成功
        PutObjectResult mockUploadResult = mock(PutObjectResult.class);
        when(obsClient.putObject(any())).thenReturn(mockUploadResult);
        
        // 模拟删除成功
        doNothing().when(obsClient).deleteObject(testBucket, testKey);
        
        // 1. 上传文件
        String uploadUrl = obsManager.uploadFile(testKey, testImageFile);
        assertNotNull(uploadUrl, "上传应该成功");
        log.info("步骤1: 文件上传成功 - {}", uploadUrl);
        
        // 2. 提取键
        String extractedKey = obsManager.extractKeyFromUrl(uploadUrl);
        assertEquals(testKey, extractedKey, "键提取应该成功");
        log.info("步骤2: 键提取成功 - {}", extractedKey);
        
        // 3. 删除文件
        boolean deleteResult = obsManager.deleteFile(extractedKey);
        assertTrue(deleteResult, "删除应该成功");
        log.info("步骤3: 文件删除成功");
        
        // 验证所有Mock调用
        verify(obsClient, times(1)).putObject(any());
        verify(obsClient, times(1)).deleteObject(testBucket, testKey);
        verify(obsClientConfig, times(2)).getBucket(); // 上传和删除各调用一次
        verify(obsClientConfig, times(2)).getHost(); // 上传和键提取各调用一次
        
        log.info("✓ 完整业务流程测试通过");
    }
}
