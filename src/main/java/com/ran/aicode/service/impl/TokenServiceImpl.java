package com.ran.aicode.service.impl;

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

        // 管理员和会员都不消耗token
        if (isAdminUser(userId) || isMemberUser(userId)) {
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
            // 如果数据库中没有这些字段，设置默认值（这种情况应该很少见）
            if (user.getTotalTokens() == null) {
                log.warn("用户 {} 的totalTokens字段为null，设置默认值50000", userId);
                user.setTotalTokens(50000L); // 设置默认值
            }
            if (user.getUsedTokens() == null) {
                log.warn("用户 {} 的usedTokens字段为null，设置默认值0", userId);
                user.setUsedTokens(0L); // 设置默认值
            }
            
            log.debug("用户 {} token信息: 总token={}, 已使用={}, 剩余={}", 
                userId, user.getTotalTokens(), user.getUsedTokens(), user.getRemainingTokens());
        }
        
        return user;
    }

    @Override
    public boolean isAdminUser(Long userId) {
        if (userId == null) {
            return false;
        }

        User user = getUserTokenInfo(userId);
        // 只有管理员不受token限制
        return user != null && "admin".equals(user.getUserRole());
    }

    @Override
    public boolean isMemberUser(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = getUserTokenInfo(userId);
        return user != null && "member".equals(user.getUserRole());
    }

    @Override
    public long getRemainingTokens(Long userId) {
        User user = getUserTokenInfo(userId);
        if (user == null) {
            return 0L;
        }
        Long remaining = user.getRemainingTokens();
        return remaining != null ? remaining : 0L;
    }

    // 简易预占实现：直接在 usedTokens 中加上预估值，并在 reservationId 层面不持久化记录。
    // 如需更严谨的并发与幂等，可引入数据库记录或分布式锁。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean preConsume(Long userId, long estimatedTokens, String reservationId) {
        if (userId == null || estimatedTokens <= 0) {
            return false;
        }
        // 管理员或会员不需要预占
        if (isAdminUser(userId)) {
            return true;
        }
        User user = getUserTokenInfo(userId);
        if (user == null) {
            return false;
        }
        long remaining = getRemainingTokens(userId);
        if (remaining < estimatedTokens) {
            return false;
        }
        long newUsed = (user.getUsedTokens() != null ? user.getUsedTokens() : 0L) + estimatedTokens;
        User update = new User();
        update.setId(userId);
        update.setUsedTokens(newUsed);
        return userService.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long userId, long tokens, String reservationId) {
        if (userId == null || tokens <= 0) {
            return;
        }
        // 管理员或会员无操作
        if (isAdminUser(userId)) {
            return;
        }
        User user = getUserTokenInfo(userId);
        if (user == null) {
            return;
        }
        long currentUsed = user.getUsedTokens() != null ? user.getUsedTokens() : 0L;
        long newUsed = Math.max(0L, currentUsed - tokens);
        User update = new User();
        update.setId(userId);
        update.setUsedTokens(newUsed);
        userService.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustToActual(Long userId, long actualTokens, String reservationId) {
        if (userId == null || actualTokens < 0) {
            return;
        }
        // 管理员或会员无操作
        if (isAdminUser(userId)) {
            return;
        }
        // 先读取当前 used，再根据差额做多退少补。
        User user = getUserTokenInfo(userId);
        if (user == null) {
            return;
        }
        long currentUsed = user.getUsedTokens() != null ? user.getUsedTokens() : 0L;
        // 实际消耗与当前 used 的差异（这里假设预占已计入 used，且期间没有其他操作影响 used）
        // 若实际消耗更少，则退回差额；若更多（通常不应发生），则追加到 used。
        // 为稳妥，仅以差额方向性调整。
        long estimatedPortion = 0L; // 简化：无法从内存拿到单笔预占，只调整到最接近实际
        // 这里采用保守策略：不做复杂追踪，直接以 consumeTokens 再扣一次实际值更稳，
        // 但我们已预占，因此：先全部退款预占，再按实际值扣一次。
        // 简化为：退款到原点 -> 再 consume 实际值。
        // 由于没有记录预占值，这里无法计算退款量，选择不做调整，交由调用端在拿到实际值时直接调用 consumeTokens 覆盖。
        // 若需严格调整，建议引入 reservation 记录存储。
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
