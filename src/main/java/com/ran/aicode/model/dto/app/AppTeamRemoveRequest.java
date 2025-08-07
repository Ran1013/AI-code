package com.ran.aicode.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用团队移除用户请求
 *
 * @author <a>ran</a>
 */
@Data
public class AppTeamRemoveRequest implements Serializable {

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 用户ID
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
