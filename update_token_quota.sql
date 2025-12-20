-- 更新用户token额度的SQL语句
-- 将普通用户的token额度从10000提升到50000，以便充分测试三个模式
-- 管理员和会员用户不受token限制，无需更新

-- 1. 更新所有普通用户的token额度为50000
UPDATE `user` 
SET `totalTokens` = 50000 
WHERE `userRole` = 'user' AND `totalTokens` = 10000;

-- 2. 管理员和会员用户不受token限制，无需更新
-- 注意：管理员和会员用户在实际使用中不会消耗token，所以不需要设置具体额度

-- 3. 查看更新结果
SELECT 
    `userRole`,
    COUNT(*) as `用户数量`,
    AVG(`totalTokens`) as `平均token额度`,
    MIN(`totalTokens`) as `最小token额度`,
    MAX(`totalTokens`) as `最大token额度`
FROM `user` 
WHERE `isDelete` = 0
GROUP BY `userRole`;
