package com.fugary.simple.api.web.vo.project;

import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import lombok.Data;

import java.util.List;

/** 当前编辑内容的只读 Markdown 预览，不读取或保存项目数据。 */
@Data
public class SchemaMarkdownVo {
    private String schemaContent;
    private String specVersion;
    private List<ApiProjectInfoDetail> componentSchemas;
}
