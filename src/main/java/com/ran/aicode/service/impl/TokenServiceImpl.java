package com.ran.aicode.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.ran.aicode.model.entity.User;
import com.ran.aicode.service.TokenService;
import com.ran.aicode.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

/**
 * Token服务实现类
 * 
 * @author ran
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    @Resource
    private UserService userService;

    @Override
    public boolean hasEnoughTokens(Long userId, Long requiredTokens) {
        if (userId == null || requiredTokens == null || requiredTokens <= 0) {
            return false;
        }

        // 管理员不受token限制
        if (isAdminUser(userId)) {
            return true;
        }

        User user = getUserTokenInfo(userId);
        if (user == null) {
            log.warn("用户不存在: {}", userId);
            return false;
        }

        Long remainingTokens = user.getRemainingTokens();
        return remainingTokens >= requiredTokens;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean consumeTokens(Long userId, Long consumedTokens) {
        if (userId == null || consumedTokens == null || consumedTokens <= 0) {
            return false;
        }

        // 管理员和会员不消耗token
        if (isAdminUser(userId)) {
            log.info("管理员或会员用户 {} 不消耗token", userId);
            return true;
        }

        User user = getUserTokenInfo(userId);
        if (user == null) {
            log.warn("用户不存在: {}", userId);
            return false;
        }

        // 检查是否有足够的token
        if (!hasEnoughTokens(userId, consumedTokens)) {
            log.warn("用户 {} token不足，需要: {}, 剩余: {}", userId, consumedTokens, user.getRemainingTokens());
            return false;
        }

        // 更新已使用的token数量
        Long newUsedTokens = (user.getUsedTokens() != null ? user.getUsedTokens() : 0L) + consumedTokens;
        
        // 创建更新对象
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setUsedTokens(newUsedTokens);
        
        boolean updateResult = userService.updateById(updateUser);

        if (updateResult) {
            log.info("用户 {} 消耗token成功，消耗: {}, 剩余: {}", userId, consumedTokens, user.getRemainingTokens() - consumedTokens);
        } else {
            log.error("用户 {} 消耗token失败", userId);
        }

        return updateResult;
    }

    @Override
    public User getUserTokenInfo(Long userId) {
        if (userId == null) {
            return null;
        }

        // 使用更直接的 getById 方法，避免 QueryWrapper 的参数绑定问题
        User user = userService.getById(userId);
        
        // 确保返回的User对象包含所有需要的token信息字段
        if (user != null) {
            // 如果数据库中没有这些字段，设置默认值
            if (user.getTotalTokens() == null) {
                user.setTotalTokens(10000L); // 设置默认值
            }
            if (user.getUsedTokens() == null) {
                user.setUsedTokens(0L); // 设置默认值
            }
        }
        
        return user;
    }

    @Override
    public boolean isAdminUser(Long userId) {
        if (userId == null) {
            return false;
        }

        User user = getUserTokenInfo(userId);
        return user != null && ("admin".equals(user.getUserRole()) || "member".equals(user.getUserRole()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetUserTokens(Long userId, Long totalTokens) {
        if (userId == null || totalTokens == null || totalTokens < 0) {
            return false;
        }

        // 创建更新对象
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setTotalTokens(totalTokens);
        updateUser.setUsedTokens(0L);
        
        boolean updateResult = userService.updateById(updateUser);

        if (updateResult) {
            log.info("用户 {} token重置成功，新额度: {}", userId, totalTokens);
        } else {
            log.error("用户 {} token重置失败", userId);
        }

        return updateResult;
    }
}
