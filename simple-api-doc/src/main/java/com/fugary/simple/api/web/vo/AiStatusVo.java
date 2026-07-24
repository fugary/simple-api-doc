package com.fugary.simple.api.web.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 状态与可用配置 VO
 */
@Data
public class AiStatusVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否启用 AI
     */
    private boolean enabled;

    /**
     * 默认的 AI 配置 ID
     */
    private Integer defaultConfigId;

    /**
     * 可用的 AI 配置列表
     */
    private List<AiConfigVo> configs;

    @Data
    public static class AiConfigVo implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer id;
        private String configName;
        private String provider;
        private String defaultModel;
        private Integer isDefault;
    }
}
