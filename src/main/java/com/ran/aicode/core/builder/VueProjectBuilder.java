package com.ran.aicode.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import com.ran.aicode.websocket.AppEditHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 构建 Vue 项目
 */
@Slf4j
@Component
public class VueProjectBuilder {

    @Resource
    private AppEditHandler appEditHandler;

    /**
     * 构建状态枚举
     */
    public enum BuildStatus {
        BUILDING("构建中"),
        SUCCESS("构建成功"),
        FAILED("构建失败");

        private final String text;

        BuildStatus(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 构建状态管理
     */
    private static final Map<Long, BuildStatus> buildStatusMap = new ConcurrentHashMap<>();

    /**
     * 获取构建状态
     *
     * @param appId 应用ID
     * @return 构建状态
     */
    public BuildStatus getBuildStatus(Long appId) {
        return buildStatusMap.getOrDefault(appId, BuildStatus.FAILED);
    }

    /**
     * 清理构建状态
     *
     * @param appId 应用ID
     */
    public void clearBuildStatus(Long appId) {
        buildStatusMap.remove(appId);
    }

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath
     * @param appId
     * @param userId
     */
    public void buildProjectAsync(String projectPath, Long appId, Long userId) {
        // 设置构建状态
        buildStatusMap.put(appId, BuildStatus.BUILDING);
        log.info("开始异步构建Vue项目: appId={}, 状态={}", appId, BuildStatus.BUILDING.getText());

        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    boolean success = false;
                    try {
                        success = buildProject(projectPath);

                        if (success) {
                            buildStatusMap.put(appId, BuildStatus.SUCCESS);
                            log.info("Vue项目构建成功: appId={}, 状态={}", appId, BuildStatus.SUCCESS.getText());
                        } else {
                            buildStatusMap.put(appId, BuildStatus.FAILED);
                            log.error("Vue项目构建失败: appId={}, 状态={}", appId, BuildStatus.FAILED.getText());
                        }
                    } catch (Exception e) {
                        buildStatusMap.put(appId, BuildStatus.FAILED);
                        log.error("异步构建 Vue 项目时发生异常: appId={}, error={}", appId, e.getMessage(), e);
                    }

                    // 多重试通知机制
                    for (int i = 0; i < 3; i++) {
                        try {
                            appEditHandler.notifyVueBuildComplete(appId);
                            log.info("Vue构建完成通知已发送: appId={}, 尝试={}/3, 成功={}", appId, i + 1, success);
                            if (i < 2) {
                                Thread.sleep(1000); // 等待1秒再重试
                            }
                            break; // 发送成功就退出
                        } catch (Exception e) {
                            log.warn("发送Vue构建完成通知失败，重试: appId={}, 尝试={}/3, error={}", appId, i + 1, e.getMessage());
                            if (i < 2) {
                                try {
                                    Thread.sleep(1000); // 等待1秒后重试
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    }

                    // 30分钟后清理状态
                    Thread.ofVirtual().start(() -> {
                        try {
                            Thread.sleep(TimeUnit.MINUTES.toMillis(30));
                            buildStatusMap.remove(appId);
                            log.info("已清理过期的构建状态: appId={}", appId);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                });
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在：{}", projectPath);
            return false;
        }
        // 检查是否有 package.json 文件，如果没有则自动创建
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            log.warn("项目目录中没有 package.json 文件，正在自动创建：{}", projectPath);
            if (!createDefaultPackageJson(packageJsonFile)) {
                log.error("自动创建 package.json 失败：{}", projectPath);
                return false;
            }
            log.info("已自动创建 package.json 文件：{}", projectPath);
        }

        log.info("开始构建 Vue 项目：{}", projectPath);

        // 结构校验（在构建前检查关键文件和路由-页面一致性）
        if (!validateProjectStructure(projectDir)) {
            log.error("Vue 项目结构校验失败，终止构建: {}", projectPath);
            return false;
        }

        // 智能清理构建文件
        smartCleanupBuildFiles(projectDir);

        // 首次尝试构建
        if (attemptBuild(projectDir)) {
            log.info("Vue 项目构建成功，dist 目录：{}", projectPath);
            return true;
        }

        // 首次失败，清理 node_modules 后重试
        log.warn("首次构建失败，清理依赖后重试...");
        cleanupNodeModulesOnly(projectDir);

        if (attemptBuild(projectDir)) {
            log.info("清理依赖后 Vue 项目构建成功，dist 目录：{}", projectPath);
            return true;
        }

        log.error("清理依赖后构建仍然失败");
        return false;
    }

    /**
     * 创建默认的 package.json 文件
     *
     * @param packageJsonFile package.json 文件对象
     * @return 是否创建成功
     */
    private boolean createDefaultPackageJson(File packageJsonFile) {
        try {
            String packageJsonContent = """
                {
                  "name": "vue-project",
                  "version": "1.0.0",
                  "description": "Vue.js project generated by AI",
                  "main": "index.js",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build",
                    "preview": "vite preview"
                  },
                  "dependencies": {
                    "vue": "^3.4.0",
                    "vue-router": "^4.2.0"
                  },
                  "devDependencies": {
                    "@vitejs/plugin-vue": "^4.5.0",
                    "vite": "^5.0.0"
                  }
                }
                """;
            
            try (FileWriter writer = new FileWriter(packageJsonFile)) {
                writer.write(packageJsonContent);
                writer.flush();
            }
            
            log.info("成功创建默认 package.json 文件：{}", packageJsonFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("创建 package.json 文件失败：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }

    /**
     * 根据操作系统构造命令
     *
     * @param baseCommand
     * @return
     */
    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 操作系统检测
     *
     * @return
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    command.split("\\s+") // 命令分割为数组
            );
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }

    /**
     * 智能清理构建文件
     * 只清理必要的文件，保留有效的依赖缓存以提升构建速度
     *
     * @param projectDir 项目目录
     */
    private void smartCleanupBuildFiles(File projectDir) {
        try {
            // 1. 总是清理 dist 目录（构建输出目录）
            File distDir = new File(projectDir, "dist");
            if (distDir.exists()) {
                deleteDirectory(distDir);
                log.info("已清理旧的 dist 目录: {}", distDir.getAbsolutePath());
            }

            // 2. 智能判断是否需要清理 node_modules
            File packageJsonFile = new File(projectDir, "package.json");
            File nodeModulesDir = new File(projectDir, "node_modules");
            File packageLockFile = new File(projectDir, "package-lock.json");

            // 检查是否需要清理 node_modules
            boolean needCleanNodeModules = false;

            if (!nodeModulesDir.exists()) {
                // node_modules 不存在，不需要清理
                needCleanNodeModules = false;
                log.debug("node_modules 不存在，跳过清理");
            } else if (!packageLockFile.exists()) {
                // 没有 package-lock.json，需要清理重装
                needCleanNodeModules = true;
                log.info("检测到缺少 package-lock.json，将清理 node_modules");
            } else {
                // 检查 node_modules 是否为空或损坏
                File[] files = nodeModulesDir.listFiles();
                if (files == null || files.length == 0) {
                    needCleanNodeModules = true;
                    log.info("检测到 node_modules 为空，将清理重装");
                } else {
                    // 检查核心依赖是否存在
                    boolean hasVue = new File(nodeModulesDir, "vue").exists();
                    boolean hasVite = new File(nodeModulesDir, "vite").exists();
                    if (!hasVue || !hasVite) {
                        needCleanNodeModules = true;
                        log.info("检测到核心依赖缺失（vue: {}, vite: {}），将清理重装", hasVue, hasVite);
                    }
                }
            }

            if (needCleanNodeModules) {
                deleteDirectory(nodeModulesDir);
                log.info("已清理旧的 node_modules 目录: {}", nodeModulesDir.getAbsolutePath());
            } else {
                log.info("node_modules 状态良好，保留缓存以提升构建速度");
            }

        } catch (Exception e) {
            log.error("智能清理构建文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 尝试构建项目
     *
     * @param projectDir 项目目录
     * @return 是否构建成功
     */
    private boolean attemptBuild(File projectDir) {
        // 执行 npm install
        if (!executeNpmInstall(projectDir)) {
            log.debug("npm install 执行失败");
            return false;
        }

        // 执行 npm run build
        if (!executeNpmBuild(projectDir)) {
            log.debug("npm run build 执行失败");
            return false;
        }

        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.debug("构建完成但 dist 目录未生成");
            return false;
        }

        return true;
    }

    /**
     * 仅清理 node_modules 目录
     *
     * @param projectDir 项目目录
     */
    private void cleanupNodeModulesOnly(File projectDir) {
        try {
            File nodeModulesDir = new File(projectDir, "node_modules");
            if (nodeModulesDir.exists()) {
                deleteDirectory(nodeModulesDir);
                log.info("已清理 node_modules 目录: {}", nodeModulesDir.getAbsolutePath());
            }

            // 同时清理 package-lock.json，确保重新生成
            File packageLockFile = new File(projectDir, "package-lock.json");
            if (packageLockFile.exists()) {
                packageLockFile.delete();
                log.info("已清理 package-lock.json 文件: {}", packageLockFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("清理 node_modules 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 递归删除目录及其所有内容
     *
     * @param directory 要删除的目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * 校验 Vue 项目基本结构（并视情况自动补救）
     * 返回 true 表示可以进入构建阶段；false 表示结构问题严重，直接终止构建。
     *
     * @param projectDir 项目目录
     * @return 是否通过校验
     */
    private boolean validateProjectStructure(File projectDir) {
        log.info("开始校验 Vue 项目结构: {}", projectDir.getAbsolutePath());

        // 1. 检查关键文件/目录是否存在
        File packageJsonFile = new File(projectDir, "package.json");
        File srcMainFile = new File(projectDir, "src/main.js");
        File srcMainTsFile = new File(projectDir, "src/main.ts");
        File appVueFile = new File(projectDir, "src/App.vue");
        File routerIndexJs = new File(projectDir, "src/router/index.js");
        File routerIndexTs = new File(projectDir, "src/router/index.ts");
        File pagesDir = new File(projectDir, "src/pages");

        if (!packageJsonFile.exists()) {
            log.error("缺少关键文件: package.json");
            return false;
        }

        boolean hasMainJs = srcMainFile.exists() || srcMainTsFile.exists();
        if (!hasMainJs) {
            log.error("缺少关键文件: src/main.js 或 src/main.ts");
            return false;
        }

        if (!appVueFile.exists()) {
            log.error("缺少关键文件: src/App.vue");
            return false;
        }

        boolean hasRouter = routerIndexJs.exists() || routerIndexTs.exists();
        if (!hasRouter) {
            log.error("缺少关键文件: src/router/index.js 或 src/router/index.ts");
            return false;
        }

        if (!pagesDir.exists() || !pagesDir.isDirectory()) {
            log.error("缺少关键目录: src/pages");
            return false;
        }

        // 2. 校验"路由–页面一致性"
        File routerFile = routerIndexJs.exists() ? routerIndexJs : routerIndexTs;
        try {
            String routerContent = java.nio.file.Files.readString(routerFile.toPath());
            if (routerContent == null || routerContent.isEmpty()) {
                log.warn("路由文件内容为空: {}", routerFile.getAbsolutePath());
                return true; // 允许构建，可能是空项目
            }

            // 解析路由组件路径
            java.util.Set<String> routeComponentPaths = extractRouteComponentPaths(routerContent);
            log.info("从路由文件中解析到 {} 个路由组件路径", routeComponentPaths.size());

            // 检查对应的页面文件是否存在
            java.util.List<String> missingFiles = new java.util.ArrayList<>();
            for (String componentPath : routeComponentPaths) {
                // 将 @/pages/XXX.vue 转换为 src/pages/XXX.vue
                String relativePath = componentPath.replace("@/pages/", "src/pages/");
                File pageFile = new File(projectDir, relativePath);
                if (!pageFile.exists()) {
                    missingFiles.add(relativePath);
                }
            }

            if (!missingFiles.isEmpty()) {
                log.warn("检测到 {} 个缺失的页面文件: {}", missingFiles.size(), missingFiles);

                // 3. 对缺失的页面文件执行"自动补救"
                if (missingFiles.size() >= 5) {
                    log.error("缺失文件数量过多（{}），自动补救失败，终止构建", missingFiles.size());
                    return false;
                }

                boolean allRemedied = true;
                for (String missingFile : missingFiles) {
                    // 从路径中提取页面名称（如 src/pages/ProductDetail.vue -> ProductDetail）
                    String fileName = new File(missingFile).getName();
                    String pageName = fileName.substring(0, fileName.lastIndexOf('.'));
                    String routePath = missingFile.replace("src/pages/", "/").replace(".vue", "");

                    log.info("自动创建占位页面: {} (路由: {})", missingFile, routePath);
                    if (!createPlaceholderPage(projectDir, missingFile, pageName)) {
                        log.error("创建占位页面失败: {}", missingFile);
                        allRemedied = false;
                    }
                }

                if (allRemedied) {
                    log.info("已自动创建 {} 个占位页面，构建将继续", missingFiles.size());
                } else {
                    log.error("部分占位页面创建失败，终止构建");
                    return false;
                }
            }

        } catch (IOException e) {
            log.error("读取路由文件失败: {}, 错误: {}", routerFile.getAbsolutePath(), e.getMessage(), e);
            return false;
        }

        log.info("Vue 项目结构校验通过");
        return true;
    }

    /**
     * 从路由文件内容中提取所有路由组件路径
     * 支持动态 import 和静态 import 两种形式
     *
     * @param routerContent 路由文件内容
     * @return 路由组件路径集合（如 @/pages/Home.vue）
     */
    private java.util.Set<String> extractRouteComponentPaths(String routerContent) {
        java.util.Set<String> paths = new java.util.HashSet<>();

        // 匹配动态 import: component: () => import('@/pages/XXX.vue')
        java.util.regex.Pattern dynamicPattern = java.util.regex.Pattern.compile(
                "import\\(['\"](@/pages/[^'\"]+)['\"]\\)",
                java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher dynamicMatcher = dynamicPattern.matcher(routerContent);
        while (dynamicMatcher.find()) {
            paths.add(dynamicMatcher.group(1));
        }

        // 匹配静态 import: import XXX from '@/pages/XXX.vue'
        java.util.regex.Pattern staticPattern = java.util.regex.Pattern.compile(
                "import\\s+\\w+\\s+from\\s+['\"](@/pages/[^'\"]+)['\"]",
                java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher staticMatcher = staticPattern.matcher(routerContent);
        while (staticMatcher.find()) {
            paths.add(staticMatcher.group(1));
        }

        return paths;
    }

    /**
     * 创建占位页面文件
     *
     * @param projectDir 项目目录
     * @param relativePath 相对路径（如 src/pages/ProductDetail.vue）
     * @param pageName 页面名称
     * @return 是否创建成功
     */
    private boolean createPlaceholderPage(File projectDir, String relativePath, String pageName) {
        try {
            File pageFile = new File(projectDir, relativePath);
            File parentDir = pageFile.getParentFile();

            // 确保父目录存在
            if (parentDir != null && !parentDir.exists()) {
                java.nio.file.Files.createDirectories(parentDir.toPath());
            }

            String content = String.format("""
<template>
  <div class="page-placeholder">
    <h1>%s 页面</h1>
    <p>此页面为自动生成的占位内容</p>
  </div>
</template>

<script setup>
const title = '%s 页面'
</script>

<style scoped>
.page-placeholder {
  padding: 40px;
  text-align: center;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.page-placeholder h1 {
  font-size: 32px;
  color: #333;
  margin-bottom: 16px;
}

.page-placeholder p {
  font-size: 16px;
  color: #666;
}
</style>
""", pageName, pageName);

            java.nio.file.Files.writeString(pageFile.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
            log.info("成功创建占位页面: {}", pageFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            log.error("创建占位页面失败: {}, 错误: {}", relativePath, e.getMessage(), e);
            return false;
        }
    }

}
