package com.fugary.simple.api.web.controllers.generator.vo;

import lombok.Data;

import java.util.Map;

/**
 * Create date 2025/10/27<br>
 *
 * @author gary.fu
 */
@Data
public class GenRequestVo {
    private String openAPIUrl;              // 远程地址
    private Map<String, Object> options;    // 生成器参数
    private Map<String, Object> spec;       // 直接传 OpenAPI 文档
}
