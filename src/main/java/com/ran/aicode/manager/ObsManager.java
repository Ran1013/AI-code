package com.ran.aicode.manager;

import com.obs.services.ObsClient;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import com.ran.aicode.config.ObsClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * OBS 对象存储管理器
 */
@Component
@Slf4j
public class ObsManager {

    @Resource
    private ObsClientConfig obsClientConfig;

    @Resource
    private ObsClient obsClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(obsClientConfig.getBucket(), key, file);
        return obsClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 OBS 并返回访问 URL
     *
     * @param key  OBS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        try {
            putObject(key, file);
            String url = String.format("%s/%s", obsClientConfig.getHost(), key);
            log.info("文件上传到 OBS 成功：{} -> {}", file.getName(), url);
            return url;
        } catch (Exception e) {
            log.error("文件上传到 OBS 失败：{}，错误信息：{}", file.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 从 OBS 删除文件
     *
     * @param key OBS对象键（完整路径）
     * @return 是否删除成功
     */
    public boolean deleteFile(String key) {
        try {
            obsClient.deleteObject(obsClientConfig.getBucket(), key);
            log.info("文件从 OBS 删除成功：{}", key);
            return true;
        } catch (Exception e) {
            log.error("文件从 OBS 删除失败：{}，错误: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 从 URL 中提取 OBS 对象键
     *
     * @param url 文件访问URL
     * @return OBS对象键
     */
    public String extractKeyFromUrl(String url) {
        if (url == null || !url.startsWith(obsClientConfig.getHost())) {
            return null;
        }
        return url.substring(obsClientConfig.getHost().length() + 1);
    }
}

