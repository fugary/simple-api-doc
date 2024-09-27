package com.fugary.simple.api.web.vo.project;

import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApiProjectDetailVo extends ApiProject {

    private List<ApiProjectInfo> infoList = new ArrayList<>();
    private List<ApiFolder> folders = new ArrayList<>();
    private List<ApiDoc> docs = new ArrayList<>();
}
