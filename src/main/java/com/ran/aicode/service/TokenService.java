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
     * 是否为会员用户
     */
    boolean isMemberUser(Long userId);

    /**
     * 获取剩余 token 数
     */
    long getRemainingTokens(Long userId);

    /**
     * 预占 token（开始前锁定额度）。若成功，会立即从已用额度中预扣。
     * 完成后需调用 adjust/refund 进行结算。
     */
    boolean preConsume(Long userId, long estimatedTokens, String reservationId);

    /**
     * 退款（用于实际消耗小于预估时退回差额，或失败时退回全部）。
     */
    void refund(Long userId, long tokens, String reservationId);

    /**
     * 按实际消耗结算（多退少补）。
     */
    void adjustToActual(Long userId, long actualTokens, String reservationId);

    /**
     * 重置用户token（管理员功能）
     * 
     * @param userId 用户ID
     * @param totalTokens 新的总token数量
     * @return 是否重置成功
     */
    boolean resetUserTokens(Long userId, Long totalTokens);
}
