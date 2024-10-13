package com.fugary.simple.api.web.vo.project;

import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import lombok.Data;

@Data
public class AdminProjectShareVo extends ApiProjectShare {

    private static final long serialVersionUID = 7016926965888466762L;
    private ApiProject project;
}
