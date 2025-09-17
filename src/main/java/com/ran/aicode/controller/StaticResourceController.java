package com.ran.aicode.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ran.aicode.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

/**
 * 静态资源访问
 */
@RestController
@RequestMapping("/static")
@Slf4j
public class StaticResourceController {

    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     * 支持Vue项目的dist目录访问
     */
    @GetMapping("/{deployKey}/**")
    @SaCheckLogin
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            
            log.info("静态资源访问请求 - deployKey: {}, resourcePath: {}", deployKey, resourcePath);
            
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            
            // 构建文件路径
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            
            log.info("尝试访问文件: {}", filePath);
            
            // 如果文件不存在，尝试查找Vue项目的dist目录
            if (!file.exists() && deployKey.startsWith("vue_project_")) {
                // 对于Vue项目，尝试在dist目录中查找
                String distFilePath = PREVIEW_ROOT_DIR + "/" + deployKey + "/dist" + resourcePath;
                File distFile = new File(distFilePath);
                log.info("Vue项目，尝试dist目录: {}", distFilePath);
                if (distFile.exists()) {
                    file = distFile;
                    log.info("找到dist文件: {}", distFilePath);
                }
            }
            
            // 检查文件是否存在
            if (!file.exists()) {
                log.warn("文件不存在: {}", file.getAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            
            log.info("返回文件: {}", file.getAbsolutePath());
            
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(file.getAbsolutePath()))
                    .body(resource);
        } catch (Exception e) {
            log.error("静态资源访问异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
