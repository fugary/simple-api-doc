package com.fugary.simple.api.web.vo.query;

import lombok.Data;

import static com.fugary.simple.api.contants.ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT;

/**
 * Create date 2025/7/8<br>
 *
 * @author gary.fu
 */
@Data
public class ProjectComponentQueryVo extends ProjectQueryVo{

    private static final long serialVersionUID = 6764864260446508875L;
    private Integer infoId;
    private String bodyType = PROJECT_SCHEMA_TYPE_COMPONENT;
    private String schemaName;
}
