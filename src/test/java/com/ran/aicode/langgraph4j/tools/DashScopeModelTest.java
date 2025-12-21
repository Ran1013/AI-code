package com.ran.aicode.langgraph4j.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 测试 DashScope 模型更换后的可用性
 */
@Slf4j
@SpringBootTest
@TestPropertySource(locations = "classpath:application-prod.yml")
public class DashScopeModelTest {

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    @Test
    public void testLogoGeneration() {
        try {
            // 测试 Logo 生成
            String description = "一个现代化的技术公司Logo，蓝色调，简洁大方，无文字";
            log.info("开始测试Logo生成，描述：{}", description);

            List<ImageResource> logos = logoGeneratorTool.generateLogos(description);

            if (logos != null && !logos.isEmpty()) {
                log.info("✅ Logo生成成功！");
                log.info("生成数量：{}", logos.size());
                for (ImageResource logo : logos) {
                    log.info("Logo描述：{}", logo.getDescription());
                    log.info("Logo URL：{}", logo.getUrl());
                }
            } else {
                log.error("❌ Logo生成失败：返回结果为空");
            }

        } catch (Exception e) {
            log.error("❌ Logo生成异常：", e);
            // 打印详细的错误信息
            if (e.getMessage() != null) {
                log.error("错误信息：{}", e.getMessage());
            }
        }
    }
}