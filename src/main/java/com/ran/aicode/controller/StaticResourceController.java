package com.ran.aicode.controller;

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

    // 应用部署根目录（用于浏览）
    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;
    // 应用生成根目录（用于预览）
    private static final String OUTPUT_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     * 支持Vue项目的dist目录访问
     */
    @GetMapping("/{deployKey:[a-zA-Z0-9_-]+}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 排除特定的路径，避免与 API 文档等功能冲突
            if ("swagger".equals(deployKey) || "doc".equals(deployKey) || "api-docs".equals(deployKey)) {
                log.warn("拒绝访问保留路径: {}", deployKey);
                return ResponseEntity.notFound().build();
            }

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
            
            // 依次尝试以下路径（部署优先，其次预览目录；都尝试dist与根目录）
            String[] tryPaths = new String[]{
                    DEPLOY_ROOT_DIR + "/" + deployKey + resourcePath,
                    DEPLOY_ROOT_DIR + "/" + deployKey + "/dist" + resourcePath,
                    OUTPUT_ROOT_DIR + "/" + deployKey + resourcePath,
                    OUTPUT_ROOT_DIR + "/" + deployKey + "/dist" + resourcePath
            };
            File file = null;
            for (String p : tryPaths) {
                File f = new File(p);
                log.info("尝试访问文件: {}", p);
                if (f.exists()) {
                    file = f;
                    log.info("命中文件: {}", p);
                    break;
                }
            }
            
            // 检查文件是否存在
            if (file == null || !file.exists()) {
                log.warn("文件不存在: deployKey={}, resourcePath={}", deployKey, resourcePath);
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
