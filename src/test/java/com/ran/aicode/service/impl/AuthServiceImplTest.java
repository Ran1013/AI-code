package com.ran.aicode.service.impl;

import com.ran.aicode.service.AuthService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 认证服务测试
 */
@SpringBootTest
class AuthServiceImplTest {

    @Resource
    private AuthService authService;

    @Test
    void testSendVerifyCode() {
        // 请将下面的邮箱地址换成你自己的真实邮箱，以便查收邮件
        String email = "your-real-email@example.com";
        
        try {
            authService.sendVerifyCode(email);
            System.out.println("测试邮件发送请求已提交，请检查邮箱 " + email + " 是否收到验证码。");
            System.out.println("注意：如果 Redis 中不存在该邮箱对应的用户，可能会抛出异常，这是正常现象。测试前请确保该用户存在。");
        } catch (Exception e) {
            System.err.println("邮件发送测试失败，原因：" + e.getMessage());
            // 让测试失败
            org.junit.jupiter.api.Assertions.fail("邮件发送测试失败", e);
        }
    }
}

