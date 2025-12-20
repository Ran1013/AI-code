package com.ran.aicode.core.parser;

import com.ran.aicode.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlCodeParserTest {

    private final HtmlCodeParser parser = new HtmlCodeParser();

    @Test
    void parse_shouldExtractHtml_whenHasFencedBlockAndDoctype() {
        String codeContent = """
                一段说明
                ```html
                <!DOCTYPE html>
                <html><head><title>T</title></head><body>OK</body></html>
                ```
                结束
                """;
        var result = parser.parseCode(codeContent);
        assertNotNull(result);
        assertTrue(result.getHtmlCode().contains("<!DOCTYPE html>"));
        assertTrue(result.getHtmlCode().toLowerCase().contains("<html"));
    }

    @Test
    void parse_shouldExtractHtml_whenHasFencedBlockAndHtmlTagOnly() {
        String codeContent = """
                ```html
                <html><head></head><body>OK</body></html>
                ```
                """;
        var result = parser.parseCode(codeContent);
        assertNotNull(result);
        assertTrue(result.getHtmlCode().toLowerCase().contains("<html"));
    }

    @Test
    void parse_shouldThrow_whenMissingFencedBlock() {
        String codeContent = "我将为您创建一个简洁而专业的个人介绍页面...";
        assertThrows(BusinessException.class, () -> parser.parseCode(codeContent));
    }

    @Test
    void parse_shouldThrow_whenFencedBlockButNotCompleteHtml() {
        String codeContent = """
                ```html
                <div>only fragment</div>
                ```
                """;
        assertThrows(BusinessException.class, () -> parser.parseCode(codeContent));
    }

    @Test
    void parse_shouldThrow_whenWrongLanguageFence() {
        String codeContent = """
                ```text
                <!DOCTYPE html><html></html>
                ```
                """;
        assertThrows(BusinessException.class, () -> parser.parseCode(codeContent));
    }
}
