package com.ran.aicode.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.ran.aicode.common.PageRequest;
import com.ran.aicode.constant.RedisLuaScriptConstant;
import com.ran.aicode.constant.ThumbConstant;
import com.ran.aicode.mapper.ThumbMapper;
import com.ran.aicode.model.entity.App;
import com.ran.aicode.model.entity.Thumb;
import com.ran.aicode.model.entity.User;
import com.ran.aicode.model.enums.LuaStatusEnum;
import com.ran.aicode.model.vo.AppThumbVO;
import com.ran.aicode.service.AppService;
import com.ran.aicode.service.ThumbService;
import com.ran.aicode.service.UserService;
import com.ran.aicode.utils.RedisKeyUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Page<AppThumbVO> getAppThumbPage(PageRequest pageRequest) {
        // 创建分页对象
        Page<AppThumbVO> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        // 使用 QueryWrapper 构建查询，直接使用应用表中的 thumbCount 字段
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select("a.id, a.appName, a.thumbCount")
                .from("app").as("a")
                .where("a.isDelete = 0")
                .orderBy("a.thumbCount DESC, a.id DESC");

        return this.pageAs(page, queryWrapper, AppThumbVO.class);
    }

    @Override
    public boolean likeApp(Long appId, Long userId) {
        String timeSlice = getTimeSlice();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        // 执行 Lua 脚本
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId,
                appId
        );
        if (result == LuaStatusEnum.FAIL.getValue()) {
            throw new RuntimeException("用户已点赞");
        }
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    private String getTimeSlice() {
        DateTime nowDate = DateUtil.date();
        // 获取到当前时间前最近的整数秒，比如当前 11:20:23 ，获取到 11:20:20
        return DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;
    }
    @Override
    public boolean unlikeApp(Long appId, Long userId) {
        String timeSlice = getTimeSlice();
        // Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        // 执行 Lua 脚本
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId,
                appId
        );
        // 根据返回值处理结果
        if (result == LuaStatusEnum.FAIL.getValue()) {
            throw new RuntimeException("用户未点赞");
        }
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    @Override
    public boolean isUserLikedApp(Long appId, Long userId) {
        return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, appId.toString());
    }
}
