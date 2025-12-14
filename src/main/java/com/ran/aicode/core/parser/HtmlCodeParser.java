package com.ran.aicode.core.parser;

import com.ran.aicode.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件代码解析器
 *
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 fenced 的 HTML 代码块（严格模式）
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode == null || htmlCode.trim().isEmpty()) {
            throw new com.ran.aicode.exception.BusinessException(
                    com.ran.aicode.exception.ErrorCode.SYSTEM_ERROR,
                    "HTML 代码提取失败：未找到 ```html ... ``` 代码块"
            );
        }
        String trimmed = htmlCode.trim();
        if (!looksLikeCompleteHtml(trimmed)) {
            throw new com.ran.aicode.exception.BusinessException(
                    com.ran.aicode.exception.ErrorCode.SYSTEM_ERROR,
                    "HTML 代码提取失败：代码块缺少 <!DOCTYPE html> 或 <html>"
            );
        }
        result.setHtmlCode(trimmed);
        return result;
    }

    /**
     * 判断提取出来的内容是否像完整 HTML 文档。
     * 这里使用严格规则：必须包含 <!DOCTYPE html> 或 <html>。
     */
    private boolean looksLikeCompleteHtml(String html) {
        String lower = html.toLowerCase();
        return lower.contains("<!doctype html") || lower.contains("<html");
    }

    /**
     * 提取HTML代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
