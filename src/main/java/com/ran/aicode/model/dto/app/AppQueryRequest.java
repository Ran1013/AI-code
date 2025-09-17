package com.ran.aicode.model.dto.app;

import com.ran.aicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 应用查询请求
 *
 * @author <a>ran</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 代码生成类型
     */
    private String codeGenType;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 提示词
     */
    private String initPrompt;

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 是否为团队应用
     */
    private Integer isTeam;

    /**
     * 创建者关键字（支持用户ID / 账号 / 用户名）
     */
    private String creatorKeyword;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序
     */
    private String sortOrder;

    private static final long serialVersionUID = 1L;
} 