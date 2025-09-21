package com.ran.aicode.service;

import com.ran.aicode.model.entity.User;

/**
 * Token服务接口
 * 
 * @author ran
 */
public interface TokenService {

    /**
     * 检查用户是否有足够的token
     * 
     * @param userId 用户ID
     * @param requiredTokens 需要的token数量
     * @return 是否有足够的token
     */
    boolean hasEnoughTokens(Long userId, Long requiredTokens);

    /**
     * 消耗用户token
     * 
     * @param userId 用户ID
     * @param consumedTokens 消耗的token数量
     * @return 是否消耗成功
     */
    boolean consumeTokens(Long userId, Long consumedTokens);

    /**
     * 获取用户token信息
     * 
     * @param userId 用户ID
     * @return 用户信息（包含token信息）
     */
    User getUserTokenInfo(Long userId);

    /**
     * 检查用户是否为管理员或会员（管理员和会员不受token限制）
     * 
     * @param userId 用户ID
     * @return 是否为管理员或会员
     */
    boolean isAdminUser(Long userId);

    /**
     * 重置用户token（管理员功能）
     * 
     * @param userId 用户ID
     * @param totalTokens 新的总token数量
     * @return 是否重置成功
     */
    boolean resetUserTokens(Long userId, Long totalTokens);
}
