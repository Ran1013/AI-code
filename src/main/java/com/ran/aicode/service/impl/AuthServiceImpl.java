package com.ran.aicode.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.ran.aicode.config.TencentCloudConfig;
import com.ran.aicode.exception.BusinessException;
import com.ran.aicode.exception.ErrorCode;
import com.ran.aicode.model.entity.User;
import com.ran.aicode.service.AuthService;
import com.ran.aicode.service.TencentEmailService;
import com.ran.aicode.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
// import org.springframework.mail.SimpleMailMessage; // 旧代码依赖，已注释
// import org.springframework.mail.javamail.JavaMailSender; // 旧代码依赖，已注释
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String VERIFY_CODE_PREFIX = "verify:code:";
    private static final String VERIFY_FAIL_COUNT_PREFIX = "verify:fail:";
    private static final String SEND_COUNT_PREFIX = "verify:send:";

    private static final int CODE_TTL_MINUTES = 15;
    private static final int MAX_SEND_PER_EMAIL_PER_HOUR = 3;
    private static final int MAX_VERIFY_FAIL = 3;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private TencentCloudConfig tencentCloudConfig;

    @Resource
    private TencentEmailService tencentEmailService; // 注入新的邮件服务

    @Resource
    @Lazy
    private UserService userService;

    // @Resource
    // private JavaMailSender mailSender; // 旧代码依赖，已注释

    // @Value("${spring.mail.from}")
    // private String fromEmail; // 旧代码依赖，已注释

    @Override
    public void sendVerifyCode(String email) {
        if (StrUtil.isBlank(email)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱不能为空");
        }

        User user = userService.getUserByUserAccount(email);
        String nickname = user != null ? user.getUserName() : "用户";

        String hourKey = DateUtil.format(DateUtil.date(), "yyyyMMddHH");
        String sendCountKey = SEND_COUNT_PREFIX + email + ":" + hourKey;

        Long sendCount = redisTemplate.opsForValue().increment(sendCountKey);
        if (sendCount != null && sendCount == 1L) {
            redisTemplate.expire(sendCountKey, Duration.ofHours(1));
        }
        if (sendCount != null && sendCount > MAX_SEND_PER_EMAIL_PER_HOUR) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该邮箱接收过于频繁，请稍后再试");
        }

        String code = RandomUtil.randomNumbers(6);

        String codeKey = VERIFY_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);

        String failKey = VERIFY_FAIL_COUNT_PREFIX + email;
        redisTemplate.delete(failKey);

        // --- 调用新的邮件服务 ---
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("nickname", nickname);
        templateData.put("code", code);
        templateData.put("minutes", CODE_TTL_MINUTES);

        tencentEmailService.sendTemplateEmail(
                email,
                "【安全验证】登录验证码",
                tencentCloudConfig.getSes().getTemplateId(),
                templateData
        );
        
        // 旧的邮件发送调用
        // sendMail(email, nickname, code, "LOGIN");
    }

    @Override
    public boolean verifyCode(Long userId, String code) {
        if (userId == null || userId <= 0 || StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }

        User user = userService.getById(userId);
        if (user == null || StrUtil.isBlank(user.getUserAccount())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或邮箱为空");
        }

        String email = user.getUserAccount();
        return verifyCodeByEmail(email, code);
    }

    @Override
    public boolean verifyCodeByEmail(String email, String code) {
        if (StrUtil.isBlank(email) || StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱和验证码不能为空");
        }

        String codeKey = VERIFY_CODE_PREFIX + email;
        Object cached = redisTemplate.opsForValue().get(codeKey);

        if (cached == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码不存在或已过期");
        }

        String cachedCode = String.valueOf(cached);
        if (!StrUtil.equals(cachedCode, code)) {
            String failKey = VERIFY_FAIL_COUNT_PREFIX + email;
            Long fail = redisTemplate.opsForValue().increment(failKey);
            if (fail != null && fail == 1L) {
                redisTemplate.expire(failKey, Duration.ofMinutes(CODE_TTL_MINUTES));
            }
            if (fail != null && fail >= MAX_VERIFY_FAIL) {
                redisTemplate.delete(codeKey);
                redisTemplate.delete(failKey);
                try {
                    sendVerifyCode(email);
                } catch (Exception e) {
                    log.warn("自动重发验证码失败 email={}, error={}", email, e.getMessage());
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码错误次数过多，已重新发送，请查收邮箱");
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码错误");
        }

        redisTemplate.delete(codeKey);
        redisTemplate.delete(VERIFY_FAIL_COUNT_PREFIX + email);
        return true;
    }

    /*
    private void sendMail(String email, String nickname, String code, String scene) {
        if (mailSender == null) {
            log.warn("未配置邮件发送，跳过实际发送，email={}, code={}", email, code);
            return;
        }
        org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("【安全验证】登录验证码");
        String nick = StrUtil.blankToDefault(nickname, "用户");
        String content = StrUtil.format(
                "您好，{}：\n\n您的登录验证码为：{}。\n验证码有效期：{}分钟。请勿泄露给他人。\n（若非本人操作，请忽略本邮件）\n\n触发场景：{}",
                nick, code, CODE_TTL_MINUTES, StrUtil.blankToDefault(scene, "LOGIN")
        );
        message.setText(content);
        try {
            mailSender.send(message);
            log.info("验证码邮件发送成功，email={}, scene={}", email, scene);
        } catch (Exception ex) {
            log.error("验证码邮件发送失败，email={}, error={}", email, ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码发送失败，请检查邮箱是否正确或稍后再试");
        }
    }
    */
}
