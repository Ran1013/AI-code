package com.ran.aicode.service.impl;

import com.ran.aicode.config.TencentCloudConfig;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ran.aicode.service.TencentEmailService;

import java.util.HashMap;
import java.util.Map;

/**
 * 腾讯云邮件服务测试
 */
@SpringBootTest(classes = {TencentEmailServiceImpl.class, TencentCloudConfig.class})
@ActiveProfiles("prod") // 指定使用 application-prod.yml 配置文件
@EnableConfigurationProperties(value = TencentCloudConfig.class)
public class TencentEmailServiceTest {

    @Resource
    private TencentEmailService tencentEmailService;

    @Resource
    private TencentCloudConfig tencentCloudConfig;

    @Test
    void testSendTemplateEmail() {
        // !!! 请注意：运行前请务必修改为您的真实测试信息 !!!

        // 1. 收件人邮箱 (替换为您自己的邮箱以接收测试邮件)
        String to = "your_test_email@example.com";

        // 2. 邮件主题
        String subject = "来自 AI Code 的测试邮件";

        // 3. 从配置文件中动态获取腾讯云邮件模板 ID
        Integer templateId = tencentCloudConfig.getSes().getTemplateId();

        // 4. 模板变量数据 (key需要和您在腾讯云控制台创建的模板变量名完全一致)
        Map<String, Object> templateData = new HashMap<>();
        // 示例：假设您的模板中有一个名为 {{code}} 的变量
        templateData.put("code", "999888");
        templateData.put("nickname", "尊敬的用户"); // 添加昵称变量
        templateData.put("minutes", "10"); // 添加分钟数变量

        try {
            tencentEmailService.sendTemplateEmail(to, subject, templateId, templateData);
            System.out.println("测试邮件已尝试发送，请检查收件箱：" + to);
        } catch (Exception e) {
            System.err.println("邮件发送失败，请检查控制台错误日志和 application-prod 配置。错误信息：" + e.getMessage());
            e.printStackTrace();
        }
    }
}

