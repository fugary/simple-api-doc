package com.fugary.simple.api.service.ai.agent.tool;

import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 单次 Agent 执行上下文，携带访问范围及文档查阅记录。
 */
@Data
@Builder
public class AgentContext {
    private ApiUserVo user;
    private ApiProjectShare share;
    private Integer defaultProjectId;
    // 成功返回过详情正文的文档（可能仅部分段落），兜底时优先展示。
    @Builder.Default
    private Set<Integer> inspectedDocIds = new LinkedHashSet<>();
    // 搜索返回的候选文档；记录不替代最终权限校验。
    @Builder.Default
    private Set<Integer> discoveredDocIds = new LinkedHashSet<>();
}
