package com.fugary.simple.api.web.vo.project;

import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProjectInfoDetailVo extends ApiProjectInfo {

    private static final long serialVersionUID = 1926102166442953486L;
    private String projectCode;
    private List<ApiProjectInfoDetail> securityRequirements = new ArrayList<>();
    private List<ApiProjectInfoDetail> securitySchemas = new ArrayList<>();
    private List<ApiProjectInfoDetail> componentSchemas = new ArrayList<>();
}
