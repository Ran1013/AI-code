package com.ran.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.ran.aicode.ai.*;
import com.ran.aicode.constant.AppConstant;
import com.ran.aicode.constant.ThumbConstant;
import com.ran.aicode.core.AiCodeGeneratorFacade;
import com.ran.aicode.core.AppResourceCleaner;
import com.ran.aicode.core.builder.VueProjectBuilder;
import com.ran.aicode.core.hander.StreamHandlerExecutor;
import com.ran.aicode.exception.BusinessException;
import com.ran.aicode.exception.ErrorCode;
import com.ran.aicode.exception.ThrowUtils;
import com.ran.aicode.langgraph4j.CodeGenConcurrentWorkflow;
import com.ran.aicode.model.dto.app.AppAddRequest;
import com.ran.aicode.model.dto.app.AppQueryRequest;
import com.ran.aicode.model.entity.App;
import com.ran.aicode.mapper.AppMapper;
import com.ran.aicode.model.entity.User;
import com.ran.aicode.model.enums.*;
import com.ran.aicode.model.vo.AppVO;
import com.ran.aicode.service.*;
import com.ran.aicode.service.TokenService;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a>ran</a>
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private AppUserService appUserService;

    @Resource
    private ScreenshotService screenshotService;

    @Autowired
    private UserService userService;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private AppResourceCleaner appResourceCleaner;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private TokenService tokenService;

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser, List<String> images, boolean force) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用
        // 个人应用：只有创建者可以访问
        // 团队应用：创建者和团队成员都可以访问
        if (!appUserService.hasAppPermission(appId, loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        
        // 4. 软拦截：管理员/会员跳过，普通用户进行余额估算校验
        if (!tokenService.isAdminUser(loginUser.getId())) {
            long estimatedTokens = estimateRequiredTokens(message, app.getCodeGenType());
            long remaining = tokenService.getRemainingTokens(loginUser.getId());
            if (remaining < estimatedTokens && !force) {
                String msg = String.format("token 不足，需 ≥%d，当前剩余 %d，请联系管理员。", estimatedTokens, remaining);
                throw new BusinessException(ErrorCode.TOKEN_INSUFFICIENT, msg);
            }
        }
        // 5. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        log.info("chatToGenCode AI回复:  appId:{}", appId);

        // 6. 构建包含图片的完整消息
        String fullMessage = buildMessageWithImages(message, images);

        // 7. 在调用 AI 前，先保存用户消息到数据库中
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId(), ChatHistoryStatusEnum.NORMAL.getValue(), images);

        // 8. 调用 AI 生成代码（流式）
        Flux<String> codeStream;
        if (loginUser.getUserRole().equals(UserRoleEnum.MEMBER.getValue())) {
            // 会员：走工作流（无限制）
            codeStream = new CodeGenConcurrentWorkflow().executeWorkflowWithFlux(fullMessage, appId, loginUser.getId());
        } else if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // 普通用户 + Vue：预占
            long estimatedTokens = estimateRequiredTokens(message, app.getCodeGenType());
            String reservationId = "vue-" + appId + "-" + loginUser.getId() + "-" + System.currentTimeMillis();
            boolean reserved = tokenService.preConsume(loginUser.getId(), estimatedTokens, reservationId);
            if (!reserved && !force) {
                long remaining = tokenService.getRemainingTokens(loginUser.getId());
                String msg = String.format("token 不足，需 ≥%d，当前剩余 %d，请联系管理员。", estimatedTokens, remaining);
                throw new BusinessException(ErrorCode.TOKEN_INSUFFICIENT, msg);
            }
            codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStreamWithReservation(fullMessage, codeGenTypeEnum, appId, loginUser.getId(), reservationId, estimatedTokens);
        } else {
            // 普通用户 + 非 Vue：直接走统一入口（完成后按实际消费）
            codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(fullMessage, codeGenTypeEnum, appId, loginUser.getId());
        }
        // 7. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);

    }

    /**
     * 基于配置估算本次生成所需 token（软拦截用）。
     */
    private long estimateRequiredTokens(String message, String codeGenType) {
        // 默认值兜底（与 application-prod.yml 一致）
        double base = 1200, multiplier = 1.0, bufferRate = 0.15;
        if (CodeGenTypeEnum.MULTI_FILE.getValue().equals(codeGenType)) {
            base = 6000; multiplier = 2.0; bufferRate = 0.15;
        } else if (CodeGenTypeEnum.VUE_PROJECT.getValue().equals(codeGenType)) {
            base = 15000; multiplier = 3.5; bufferRate = 0.2;
        }
        int promptLen = message != null ? message.length() : 0;
        double estimate = base + (promptLen / 3.0) * multiplier;
        estimate = Math.ceil(estimate * (1.0 + bufferRate));
        return Math.max(100, (long) estimate);
    }

    /**
     * 构建包含图片的完整消息
     *
     * @param message 原始消息
     * @param images  图片列表
     * @return 包含图片的完整消息
     */
    private String buildMessageWithImages(String message, List<String> images) {
        if (CollUtil.isEmpty(images)) {
            return message;
        }

        StringBuilder fullMessage = new StringBuilder(message);

        // 添加图片信息到消息中
        for (String image : images) {
            if (StrUtil.isNotBlank(image)) {
                fullMessage.append("\n\n图片: ").append(image);
            }
        }

        return fullMessage.toString();
    }


    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验
        // 个人应用：只有创建者可以部署
        // 团队应用：创建者和团队成员都可以部署
        if (!appUserService.hasAppPermission(appId, loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 如果没有，则生成 6 位 deployKey（字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，获取原始代码生成路径（应用访问目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 构建完成后，需要将构建后的文件复制到部署目录
            sourceDir = distDir;
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 9. 更新数据库
        App updateApp = new App();
        if (app.getAppName() == null) {
            // 生成名称
            AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
            String appName = aiCodeGeneratorService.generateAppName("生成应用名称");
            ThrowUtils.throwIf(appName.length() == 0, ErrorCode.SYSTEM_ERROR, "AI 生成的名称不符合规范");
            updateApp.setAppName(appName);
        }
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 得到可访问的 URL 地址
        String appDeployUrl = String.format("%s/%s", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11. 异步生成截图并且更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程并执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新数据库的封面
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }


    /**
     * 创建新应用
     *
     * @param appAddRequest 应用创建请求对象，包含应用的基本信息
     * @param request       HTTP请求对象，用于获取当前登录用户信息
     * @return 新创建的应用ID
     */
    @Override
    public String addApp(AppAddRequest appAddRequest, HttpServletRequest request) {
        // 1. 参数校验：检查请求对象是否为空
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 2. 参数校验：检查初始化提示词是否为空
        ThrowUtils.throwIf(StrUtil.isBlank(appAddRequest.getInitPrompt()), ErrorCode.PARAMS_ERROR, "初始化提示词不能为空");

        // 3. 获取当前登录用户信息
        User loginUser = userService.getLoginUser(request);

        // 4. 创建应用实体对象
        App app = new App();
        // 5. 将请求对象属性复制到应用实体
        BeanUtil.copyProperties(appAddRequest, app);
        // 6. 设置应用创建者ID
        app.setUserId(loginUser.getId());

        // 7. 使用AI智能路由服务，根据初始化提示词自动选择代码生成类型
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(appAddRequest.getInitPrompt());
        app.setCodeGenType(selectedCodeGenType.getValue());

        // 8. 保存应用到数据库
        boolean result = this.save(app);

        // 10. 检查保存操作是否成功
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 11. 返回新创建的应用ID
        return String.valueOf(app.getId());
    }

    /**
     * 删除应用及其相关数据
     *
     * @param appId 要删除的应用ID
     * @return 删除操作是否成功
     */
    @Override
    @Transactional
    public Boolean deleteApp(Long appId) {
        // 1. 参数校验：检查应用ID是否有效
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");

        // 2. 检查应用是否存在
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 删除应用相关的聊天记录
        boolean chatHistoryDeleted = chatHistoryService.deleteByAppId(appId);
        log.info("删除应用 {} 的聊天记录，结果：{}", appId, chatHistoryDeleted);

        // 4. 删除应用团队成员关联关系
        boolean teamMembersDeleted = appUserService.removeAllUsersFromApp(appId);
        log.info("删除应用 {} 的团队成员，结果：{}", appId, teamMembersDeleted);

        // 5. 删除应用本身
        boolean appDeleted = this.removeById(appId);
        log.info("删除应用 {}，结果：{}", appId, appDeleted);

        // 6. 使用 AppResourceCleaner 异步清理应用相关的文件资源
        appResourceCleaner.cleanupAppResourcesAsync(app);

        // 7. 返回删除操作结果
        return appDeleted;
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 追加创建者信息
        try {
            if (app.getUserId() != null) {
                User creator = userService.getById(app.getUserId());
                if (creator != null) {
                    appVO.setUser(userService.getUserVO(creator));
                }
            }
        } catch (Exception ignore) {
            // 保底：不影响主流程
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        return appList.stream().map(this::getAppVO).collect(Collectors.toList());
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String codeGenType = appQueryRequest.getCodeGenType();
        Long userId = appQueryRequest.getUserId();
        String creatorKeyword = appQueryRequest.getCreatorKeyword();
        Integer priority = appQueryRequest.getPriority();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        Integer isPublic = appQueryRequest.getIsPublic();
        Integer isTeam = appQueryRequest.getIsTeam();

        QueryWrapper queryWrapper = QueryWrapper.create();

        // 只在参数不为空时才添加条件
        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(codeGenType)) {
            queryWrapper.eq("codeGenType", codeGenType);
        }
        if (userId != null) {
            queryWrapper.eq("userId", userId);
        }
        if (priority != null) {
            queryWrapper.eq("priority", priority);
        }
        if (isPublic != null) {
            queryWrapper.eq("isPublic", isPublic);
        }
        if (isTeam != null) {
            queryWrapper.eq("isTeam", isTeam);
        }
        if (StrUtil.isNotBlank(appName)) {
            queryWrapper.like("appName", appName);
        }

        // 处理创建者关键字（ID/账号/用户名）。仅当未显式传 userId 时生效
        if (userId == null && StrUtil.isNotBlank(creatorKeyword)) {
            // 尝试解析为数字ID
            Long parsedId = null;
            try {
                parsedId = Long.valueOf(creatorKeyword.trim());
            } catch (Exception ignore) {}

            if (parsedId != null) {
                queryWrapper.and("userId = ?", parsedId);
            } else {
                // 根据账号或用户名模糊匹配用户ID集合（使用子查询占位符避免获取参数列表的方法依赖）
                String likeKw = "%" + creatorKeyword + "%";
                queryWrapper.and("userId in (select id from `user` where userAccount like ? or userName like ?)", likeKw, likeKw);
            }
        }

        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间倒序
            queryWrapper.orderBy("createTime", false);
        }

        return queryWrapper;
    }

    /**
     * 先从 HotKey 读取 App 数据，如果没有则查询数据库并回填 HotKey。
     * HotKey 的 key 前缀为 APP_ID_HOTKEY_PREFIX。
     *
     * @param appId 应用 ID
     * @return App 实体，可能为 null
     */
    @Override
    public App getAppByIdWithHotKey(Long appId) {
        String cacheKey = ThumbConstant.APP_ID_HOTKEY_PREFIX + appId;
        if (JdHotKeyStore.isHotKey(cacheKey)) {
            Object cached = JdHotKeyStore.get(cacheKey);
            if (cached instanceof App) {
                return (App) cached;
            }
        }
        App app = this.getById(appId);
        if (app != null) {
            JdHotKeyStore.smartSet(cacheKey, app);
        }
        return app;
    }

    @Override
    public void removeByIdWithHotKey(Long appId) {
        String cacheKey = ThumbConstant.APP_ID_HOTKEY_PREFIX + appId;
        JdHotKeyStore.remove(cacheKey);
    }

}
