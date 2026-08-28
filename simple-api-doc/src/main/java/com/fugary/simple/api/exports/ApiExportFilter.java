package com.fugary.simple.api.exports;

import java.util.List;

/**
 * Create date 2025/11/5<br>
 *
 * @author gary.fu
 */
public interface ApiExportFilter {
    /**
     * 文档id过滤
     * @return
     */
    List<Integer> getDocIds();

    /**
     * 环境过滤
     * @return
     */
    String getEnvContent();

    /**
     * 是否包含 Frontmatter 元数据（默认 true）
     * @return
     */
    default Boolean getWithFrontmatter() {
        return true;
    }
}
