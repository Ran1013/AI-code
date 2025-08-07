package com.ran.aicode.model.dto.app;

import com.ran.aicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 应用团队成员查询请求
 *
 * @author <a>ran</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppTeamMemberQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用ID
     */
    private Long appId;

    private static final long serialVersionUID = 1L;
}
