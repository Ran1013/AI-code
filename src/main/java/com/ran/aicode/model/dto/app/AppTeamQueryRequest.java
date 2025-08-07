package com.ran.aicode.model.dto.app;

import com.ran.aicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 应用团队查询请求
 *
 * @author <a>ran</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppTeamQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 用户ID
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
