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

    /**
     * 是否将本地图片转换为 Base64 嵌入文档
     * @return null 表示由具体导出器按格式使用默认策略
     */
    default Boolean getEmbedImages() {
        return null;
    }
}
