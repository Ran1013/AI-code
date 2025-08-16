package com.ran.aicode.mapper;

import com.ran.aicode.model.entity.App;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 应用 映射层。
 *
 * @author <a>ran</a>
 */
public interface AppMapper extends BaseMapper<App> {
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}
