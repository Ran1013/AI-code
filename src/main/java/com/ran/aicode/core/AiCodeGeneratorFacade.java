package com.ran.aicode.core;

import cn.hutool.json.JSONUtil;
import com.ran.aicode.ai.AiCodeGeneratorService;
import com.ran.aicode.ai.AiCodeGeneratorServiceFactory;
import com.ran.aicode.ai.GenerationTaskManager;
import com.ran.aicode.ai.model.message.AiResponseMessage;
import com.ran.aicode.ai.model.message.ToolExecutedMessage;
import com.ran.aicode.ai.model.message.ToolRequestMessage;
import com.ran.aicode.constant.AppConstant;
import com.ran.aicode.core.builder.VueProjectBuilder;
import com.ran.aicode.core.parser.CodeParserExecutor;
import com.ran.aicode.core.saver.CodeFileSaverExecutor;
import com.ran.aicode.exception.BusinessException;
import com.ran.aicode.exception.ErrorCode;
import com.ran.aicode.model.enums.CodeGenTypeEnum;
import com.ran.aicode.service.TokenService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private GenerationTaskManager generationTaskManager;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private TokenService tokenService;
//
//    /**
//     * 统一入口：根据类型生成并保存代码
//     *
//     * @param userMessage     用户提示词
//     * @param codeGenTypeEnum 生成类型
//     * @param appId 应用 ID
//     * @return 保存的目录
//     */
//    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
//        if (codeGenTypeEnum == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
//        }
//        // 根据 appId 获取相应的 AI 服务实例
//        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
//        return switch (codeGenTypeEnum) {
//            case HTML -> {
//                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
//            }
//            case MULTI_FILE -> {
//                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
//            }
//            default -> {
//                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
//            }
//        };
//    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId 应用 ID
     * @param userId 用户ID
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, Long userId) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, userId, false);
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId 应用 ID
     * @param userId 用户ID
     * @param skipVueAsyncBuild 是否跳过 Vue 异步构建（工作流内自行构建时使用，避免重复构建并发冲突）
     * @return 流式响应
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, Long userId,
                                                  boolean skipVueAsyncBuild) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        
        // 检查是否已有正在进行的任务
        if (generationTaskManager.hasActiveTask(appId)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "该应用已有正在进行的生成任务，请等待完成或取消后再试");
        }
        
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                yield generateHtmlWithRetryStream(aiCodeGeneratorService, userMessage, appId, userId);
            }
            case MULTI_FILE -> {
                yield generateMultiFileWithRetryStream(aiCodeGeneratorService, userMessage, appId, userId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, userId, CodeGenTypeEnum.VUE_PROJECT, skipVueAsyncBuild);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口（含预占上下文）：仅 Vue 模式使用，用于根据实际消耗多退少补。
     */
    public Flux<String> generateAndSaveCodeStreamWithReservation(String userMessage, CodeGenTypeEnum codeGenTypeEnum,
                                                                 Long appId, Long userId,
                                                                 String reservationId, long estimatedTokens) {
        if (codeGenTypeEnum != CodeGenTypeEnum.VUE_PROJECT) {
            return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, userId);
        }
        if (generationTaskManager.hasActiveTask(appId)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "该应用已有正在进行的生成任务，请等待完成或取消后再试");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
        return processTokenStreamWithReservation(tokenStream, appId, userId, codeGenTypeEnum, reservationId, estimatedTokens, false);
    }

    /**
     * 取消正在进行的代码生成
     *
     * @param appId 应用ID
     * @param userId 用户ID
     * @return 是否成功取消
     */
    public boolean cancelGeneration(Long appId, Long userId) {

        return generationTaskManager.cancelTask(appId, userId);
    }

    /**
     * 获取任务管理器
     *
     * @return 任务管理器
     */
    public GenerationTaskManager getGenerationTaskManager() {
        return generationTaskManager;
    }


    /**
     * 将 TokenStream 转换为纯文本流（用于HTML和多文件模式），过滤工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId 应用ID
     * @param userId 用户ID
     * @param codeGenType 代码生成类型
     * @return Flux<String> 纯文本流式响应
     */
    private Flux<String> processTokenStreamForSimpleText(TokenStream tokenStream, Long appId, Long userId, CodeGenTypeEnum codeGenType) {
        // 部分内容收集器，用于取消时保存已生成的内容
        StringBuilder partialContentBuilder = new StringBuilder();

        return Flux.create((FluxSink<String> sink) -> {
            // 注册任务到管理器
            Disposable disposable = Flux.never().subscribe(); // 占位符，实际取消逻辑在TokenStream中

            generationTaskManager.registerTask(appId, codeGenType.getValue(), disposable, sink, userId, partialContentBuilder);

            tokenStream.onPartialResponse((String partialResponse) -> {
                        partialContentBuilder.append(partialResponse);
                        sink.next(partialResponse);
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        log.debug("忽略工具调用请求: {}", toolExecutionRequest.name());
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        log.debug("忽略工具执行结果: {}", toolExecution.request().name());
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (response == null) {
                            log.warn("收到空的完整响应，跳过处理");
                            generationTaskManager.completeTask(appId);
                            sink.complete();
                            return;
                        }

                        // 统计并更新用户token消耗
                        try {
                            dev.langchain4j.model.output.TokenUsage tokenUsage = response.metadata().tokenUsage();
                            if (tokenUsage != null) {
                                Long inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0L;
                                Long outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0L;
                                Long consumedTokens = inputTokens + outputTokens;
                                if (consumedTokens > 0) {
                                    boolean success = tokenService.consumeTokens(userId, consumedTokens);
                                    if (success) {
                                        log.info("用户 {} 消耗token成功，消耗: {}", userId, consumedTokens);
                                    } else {
                                        log.warn("用户 {} 消耗token失败，消耗: {}", userId, consumedTokens);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("统计用户token消耗失败: userId={}, error={}", userId, e.getMessage(), e);
                        }

                        // 保存代码：失败必须透传给前端（让 SSE 走 business-error）
                        String completeCode = partialContentBuilder.toString();
                        if (completeCode != null && !completeCode.trim().isEmpty()) {
                            Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                            File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                            log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
                        }

                        generationTaskManager.completeTask(appId);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        generationTaskManager.completeTask(appId);
                        sink.error(error);
                    })
                    .start();
        });
    }

    private static final String HTML_RETRY_APPEND_PROMPT = "\n\n" +
            "【严格输出要求】\n" +
            "1) 只允许输出一个代码块，且必须是 ```html 开头、``` 结尾。\n" +
            "2) 代码必须包含 <!DOCTYPE html> 或 <html>，并且是完整可运行的单文件页面。\n" +
            "3) 代码块之外禁止输出任何解释、清单、说明文字。\n";

    private static final String MULTI_FILE_RETRY_APPEND_PROMPT = "\n\n" +
            "【严格输出要求 - 多文件模式】\n" +
            "1) 必须严格输出 3 个代码块，顺序固定为：```html```、```css```、```javascript```。\n" +
            "2) 每种语言只能有 1 个代码块，且代码块中必须包含完整文件内容。\n" +
            "3) 代码块外可以有少量说明文字，但代码块标签必须严格是 ```html / ```css / ```javascript。\n" +
            "4) 如果无法完全生成 CSS 或 JS，也要输出最小可运行的占位代码，HTML 代码块必须始终存在。\n";

    /**
     * HTML 模式：失败自动重试一次。
     */
    private Flux<String> generateHtmlWithRetryStream(AiCodeGeneratorService aiCodeGeneratorService, String userMessage, Long appId, Long userId) {
        TokenStream first = aiCodeGeneratorService.generateHtmlCodeStream(appId, userMessage);
        return processHtmlTokenStreamWithRetry(first, aiCodeGeneratorService, userMessage, appId, userId, false);
    }

    private Flux<String> processHtmlTokenStreamWithRetry(TokenStream tokenStream,
                                                        AiCodeGeneratorService aiCodeGeneratorService,
                                                        String originalUserMessage,
                                                        Long appId,
                                                        Long userId,
                                                        boolean isRetry) {
        StringBuilder partialContentBuilder = new StringBuilder();
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.HTML;

        return Flux.create((FluxSink<String> sink) -> {
            Disposable disposable = Flux.never().subscribe();
            generationTaskManager.registerTask(appId, codeGenType.getValue(), disposable, sink, userId, partialContentBuilder);

            tokenStream.onPartialResponse((String partialResponse) -> {
                        partialContentBuilder.append(partialResponse);
                        sink.next(partialResponse);
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        log.debug("忽略工具调用请求: {}", toolExecutionRequest.name());
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        log.debug("忽略工具执行结果: {}", toolExecution.request().name());
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (response == null) {
                            log.warn("收到空的完整响应，跳过处理");
                            generationTaskManager.completeTask(appId);
                            sink.complete();
                            return;
                        }

                        // 统计并更新用户token消耗（保持与原逻辑一致）
                        try {
                            dev.langchain4j.model.output.TokenUsage tokenUsage = response.metadata().tokenUsage();
                            if (tokenUsage != null) {
                                Long inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0L;
                                Long outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0L;
                                Long consumedTokens = inputTokens + outputTokens;
                                if (consumedTokens > 0) {
                                    boolean success = tokenService.consumeTokens(userId, consumedTokens);
                                    if (success) {
                                        log.info("用户 {} 消耗token成功，消耗: {}", userId, consumedTokens);
                                    } else {
                                        log.warn("用户 {} 消耗token失败，消耗: {}", userId, consumedTokens);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("统计用户token消耗失败: userId={}, error={}", userId, e.getMessage(), e);
                        }

                        String completeCode = partialContentBuilder.toString();
                        if (completeCode != null && !completeCode.trim().isEmpty()) {
                            try {
                                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());

                                generationTaskManager.completeTask(appId);
                                sink.complete();
                                return;
                            } catch (com.ran.aicode.exception.BusinessException e) {
                                // HTML 解析/校验失败：自动重试一次
                                if (!isRetry) {
                                    log.warn("HTML 解析失败，准备重试一次: appId={}, error={}", appId, e.getMessage());
                                    generationTaskManager.completeTask(appId);

                                    String retryMessage = originalUserMessage + HTML_RETRY_APPEND_PROMPT;
                                    TokenStream retryStream = aiCodeGeneratorService.generateHtmlCodeStream(appId, retryMessage);

                                    processHtmlTokenStreamWithRetry(retryStream, aiCodeGeneratorService, originalUserMessage, appId, userId, true)
                                            .subscribe(sink::next, sink::error, sink::complete);
                                    return;
                                }
                                // 已重试仍失败：抛出异常给上层，让 GlobalExceptionHandler 输出 business-error
                                generationTaskManager.completeTask(appId);
                                sink.error(e);
                                return;
                            }
                        }

                        generationTaskManager.completeTask(appId);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        generationTaskManager.completeTask(appId);
                        sink.error(error);
                    })
                    .start();
        });
    }

    /**
     * MULTI_FILE 模式：失败自动重试一次。
     */
    private Flux<String> generateMultiFileWithRetryStream(
            AiCodeGeneratorService aiCodeGeneratorService,
            String userMessage,
            Long appId,
            Long userId) {

        TokenStream first = aiCodeGeneratorService.generateMultiFileCodeStream(appId, userMessage);
        return processMultiFileTokenStreamWithRetry(first, aiCodeGeneratorService, userMessage, appId, userId, false);
    }

    private Flux<String> processMultiFileTokenStreamWithRetry(TokenStream tokenStream,
                                                               AiCodeGeneratorService aiCodeGeneratorService,
                                                               String originalUserMessage,
                                                               Long appId,
                                                               Long userId,
                                                               boolean isRetry) {
        StringBuilder partialContentBuilder = new StringBuilder();
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.MULTI_FILE;

        return Flux.create((FluxSink<String> sink) -> {
            Disposable disposable = Flux.never().subscribe();
            generationTaskManager.registerTask(appId, codeGenType.getValue(), disposable, sink, userId, partialContentBuilder);

            tokenStream.onPartialResponse((String partialResponse) -> {
                        partialContentBuilder.append(partialResponse);
                        sink.next(partialResponse);
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        log.debug("忽略工具调用请求: {}", toolExecutionRequest.name());
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        log.debug("忽略工具执行结果: {}", toolExecution.request().name());
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (response == null) {
                            log.warn("收到空的完整响应，跳过处理");
                            generationTaskManager.completeTask(appId);
                            sink.complete();
                            return;
                        }

                        // 统计并更新用户token消耗（保持与原逻辑一致）
                        try {
                            dev.langchain4j.model.output.TokenUsage tokenUsage = response.metadata().tokenUsage();
                            if (tokenUsage != null) {
                                Long inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0L;
                                Long outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0L;
                                Long consumedTokens = inputTokens + outputTokens;
                                if (consumedTokens > 0) {
                                    boolean success = tokenService.consumeTokens(userId, consumedTokens);
                                    if (success) {
                                        log.info("用户 {} 消耗token成功，消耗: {}", userId, consumedTokens);
                                    } else {
                                        log.warn("用户 {} 消耗token失败，消耗: {}", userId, consumedTokens);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("统计用户token消耗失败: userId={}, error={}", userId, e.getMessage(), e);
                        }

                        String completeCode = partialContentBuilder.toString();
                        if (completeCode != null && !completeCode.trim().isEmpty()) {
                            try {
                                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());

                                generationTaskManager.completeTask(appId);
                                sink.complete();
                                return;
                            } catch (BusinessException e) {
                                if (!isRetry) {
                                    log.warn("MULTI_FILE 解析失败，准备重试一次: appId={}, error={}", appId, e.getMessage());
                                    generationTaskManager.completeTask(appId);

                                    String retryMessage = originalUserMessage + MULTI_FILE_RETRY_APPEND_PROMPT;
                                    TokenStream retryStream = aiCodeGeneratorService.generateMultiFileCodeStream(appId, retryMessage);

                                    processMultiFileTokenStreamWithRetry(
                                            retryStream, aiCodeGeneratorService,
                                            originalUserMessage, appId, userId, true)
                                            .subscribe(sink::next, sink::error, sink::complete);
                                    return;
                                }
                                // 二次失败，再把错误透传
                                generationTaskManager.completeTask(appId);
                                sink.error(e);
                                return;
                            }
                        }

                        generationTaskManager.completeTask(appId);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        generationTaskManager.completeTask(appId);
                        sink.error(error);
                    })
                    .start();
        });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId 应用ID
     * @param userId 用户ID
     * @param codeGenType 代码生成类型
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, Long userId, CodeGenTypeEnum codeGenType,
                                            boolean skipVueAsyncBuild) {
        // 部分内容收集器，用于取消时保存已生成的内容
        StringBuilder partialContentBuilder = new StringBuilder();
        
        return Flux.create((FluxSink<String> sink) -> {
            // 注册任务到管理器
            Disposable disposable = Flux.never().subscribe(); // 占位符，实际取消逻辑在TokenStream中
            
            generationTaskManager.registerTask(appId, codeGenType.getValue(), disposable, sink, userId, partialContentBuilder);
            
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        // 收集部分内容
                        partialContentBuilder.append(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        // 工具已经在 LangChain4j 内部执行了，这里只是记录结果
                        log.info("工具执行完成: toolName={}, result={}", 
                                toolExecution.request().name(), 
                                cn.hutool.core.util.StrUtil.maxLength(toolExecution.result(), 100));
                        
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (response == null) {
                            log.warn("收到空的完整响应，跳过处理");
                            generationTaskManager.completeTask(appId);
                            sink.complete();
                            return;
                        }

                        // 统计并更新用户token消耗
                        try {
                            dev.langchain4j.model.output.TokenUsage tokenUsage = response.metadata().tokenUsage();
                            if (tokenUsage != null) {
                                // 计算总token消耗（input + output）
                                Long inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0L;
                                Long outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0L;
                                Long consumedTokens = inputTokens + outputTokens;

                                if (consumedTokens > 0) {
                                    boolean success = tokenService.consumeTokens(userId, consumedTokens);
                                    if (success) {
                                        log.info("用户 {} 消耗token成功，消耗: {}", userId, consumedTokens);
                                    } else {
                                        log.warn("用户 {} 消耗token失败，消耗: {}", userId, consumedTokens);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("统计用户token消耗失败: userId={}, error={}", userId, e.getMessage(), e);
                        }

                        // 根据代码生成类型执行不同的后处理逻辑
                        try {
                            if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                                // Vue 项目默认异步构建，避免阻塞流式响应；
                                // 但工作流内会有专门的构建节点，此处需要跳过，避免同目录重复构建导致 npm 并发冲突。
                                if (!skipVueAsyncBuild) {
                                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                                    vueProjectBuilder.buildProjectAsync(projectPath, appId, userId);
                                    log.info("Vue项目构建已启动（异步）: {}", projectPath);
                                } else {
                                    log.info("已跳过 Vue 异步构建（由工作流构建节点负责）: appId={}", appId);
                                }
                            } else if (codeGenType == CodeGenTypeEnum.HTML || codeGenType == CodeGenTypeEnum.MULTI_FILE) {
                                // HTML和MULTI_FILE类型的代码保存
                                String completeCode = partialContentBuilder.toString();
                                if (completeCode != null && !completeCode.trim().isEmpty()) {
                                    // 使用执行器解析代码
                                    Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                                    // 使用执行器保存代码
                                    File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                                    log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
                                }
                            }
                        } catch (Exception e) {
                            log.error("代码保存失败: {}", e.getMessage());
                        }

                        generationTaskManager.completeTask(appId);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        generationTaskManager.completeTask(appId);
                        sink.error(error);
                    })
                    .start();
        });
    }

    /**
     * Vue 预占版本：按实际使用对预占进行多退少补，不再重复调用通用的 consumeTokens。
     */
    private Flux<String> processTokenStreamWithReservation(TokenStream tokenStream, Long appId, Long userId,
                                                           CodeGenTypeEnum codeGenType, String reservationId,
                                                           long estimatedTokens, boolean skipVueAsyncBuild) {
        StringBuilder partialContentBuilder = new StringBuilder();
        return Flux.create((FluxSink<String> sink) -> {
            Disposable disposable = Flux.never().subscribe();
            generationTaskManager.registerTask(appId, codeGenType.getValue(), disposable, sink, userId, partialContentBuilder);

            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        partialContentBuilder.append(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        log.info("工具执行完成: toolName={}, result={}",
                                toolExecution.request().name(),
                                cn.hutool.core.util.StrUtil.maxLength(toolExecution.result(), 100));
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        if (response == null) {
                            log.warn("收到空的完整响应，跳过处理");
                            generationTaskManager.completeTask(appId);
                            sink.complete();
                            return;
                        }

                        try {
                            dev.langchain4j.model.output.TokenUsage tokenUsage = response.metadata().tokenUsage();
                            if (tokenUsage != null) {
                                long inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0L;
                                long outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0L;
                                long actual = inputTokens + outputTokens;
                                if (reservationId != null && estimatedTokens > 0) {
                                    if (actual > estimatedTokens) {
                                        long delta = actual - estimatedTokens;
                                        // 预占不够，补扣差额
                                        tokenService.consumeTokens(userId, delta);
                                    } else if (actual < estimatedTokens) {
                                        long refund = estimatedTokens - actual;
                                        tokenService.refund(userId, refund, reservationId);
                                    }
                                } else {
                                    // 无预占，走通用消费
                                    if (actual > 0) {
                                        tokenService.consumeTokens(userId, actual);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("统计用户token消耗(预占)失败: userId={}, error={}", userId, e.getMessage(), e);
                        }

                        // 后处理，与通用一致
                        try {
                            if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                                if (!skipVueAsyncBuild) {
                                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                                    vueProjectBuilder.buildProjectAsync(projectPath, appId, userId);
                                    log.info("Vue项目构建已启动（异步）: {}", projectPath);
                                } else {
                                    log.info("已跳过 Vue 异步构建（预占链路）: appId={}", appId);
                                }
                            }
                        } catch (Exception e) {
                            log.error("代码保存失败: {}", e.getMessage());
                        }

                        generationTaskManager.completeTask(appId);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        // 失败则退还全部预占
                        try {
                            if (reservationId != null && estimatedTokens > 0) {
                                tokenService.refund(userId, estimatedTokens, reservationId);
                            }
                        } catch (Exception ignore) {}
                        error.printStackTrace();
                        generationTaskManager.completeTask(appId);
                        sink.error(error);
                    })
                    .start();
        });
    }
}
