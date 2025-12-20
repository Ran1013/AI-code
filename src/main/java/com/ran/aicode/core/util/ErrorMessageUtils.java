package com.ran.aicode.core.util;

/**
 * 统一错误消息清洗与归一化工具
 */
public final class ErrorMessageUtils {

    private ErrorMessageUtils() {}

    /**
     * 将第三方返回的复杂错误信息转为对用户友好的短句
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "系统错误，请稍后重试";
        }
        String msg = raw;
        // 常见提示归一化
        if (containsIgnoreCase(msg, "Range of input length")) {
            return "输入内容过长，请精简到 6000 字符以内";
        }
        if (containsIgnoreCase(msg, "Range of max_tokens") || containsIgnoreCase(msg, "max_tokens")) {
            return "生成长度超出限制，请减少输出长度或分段生成";
        }
        if (containsIgnoreCase(msg, "tool_calls") && containsIgnoreCase(msg, "must be followed")) {
            return "模型调用工具异常，请重试";
        }
        if (containsIgnoreCase(msg, "internal_server_error") || containsIgnoreCase(msg, "InternalError")) {
            return "服务繁忙，请稍后重试";
        }
        if (containsIgnoreCase(msg, "nullpointerexception") || containsIgnoreCase(msg, "null")) {
            return "系统处理异常，请重试";
        }
        if (containsIgnoreCase(msg, "argument type mismatch") || containsIgnoreCase(msg, "type mismatch")) {
            return "参数处理异常，请重试";
        }
        if (containsIgnoreCase(msg, "tool") && containsIgnoreCase(msg, "execution")) {
            return "功能调用异常，请重试";
        }
        // 默认兜底
        return "系统错误，请稍后重试";
    }

    private static boolean containsIgnoreCase(String s, String needle) {
        return s.toLowerCase().contains(needle.toLowerCase());
    }
}


