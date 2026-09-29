package com.fugary.simple.api.web.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供 AI 检索使用的紧凑文档摘要 DTO（极大节省上下文 Token）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompactSearchDocDto {
    private Integer docId;
    private String docName;
    private String docType;
    private String method;
    private String url;
    private String description;
    private Integer projectId;
    private String projectName;
    private String folderPath;
}
