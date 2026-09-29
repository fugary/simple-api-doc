package com.fugary.simple.api.service.ai.agent.tool;

import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import lombok.Builder;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Agent 执行上下文，携带当前用户或分享信息及校验白名单
 */
@Data
@Builder
public class AgentContext {
    private ApiUserVo user;
    private ApiProjectShare share;
    private Integer defaultProjectId;
    @Builder.Default
    private Set<Integer> verifiedDocIds = new HashSet<>();
    @Builder.Default
    private Set<Integer> inspectedDocIds = new HashSet<>();
}
