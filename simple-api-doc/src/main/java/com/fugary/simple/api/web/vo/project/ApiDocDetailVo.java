package com.fugary.simple.api.web.vo.project;

import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.entity.api.ApiProject;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Data
public class ApiDocDetailVo extends ApiDoc {

    private static final long serialVersionUID = 7411498052279393090L;
    private ApiDocSchema parametersSchema;
    private ApiDocSchema securityRequirements;
    private List<ApiDocSchema> requestsSchemas = new ArrayList<>();
    private List<ApiDocSchema> responsesSchemas = new ArrayList<>();
    private ApiProject project;
    private ApiProjectInfoDetailVo projectInfoDetail;
    private ApiProjectShareVo apiShare;
    private String apiMarkdown;
}
