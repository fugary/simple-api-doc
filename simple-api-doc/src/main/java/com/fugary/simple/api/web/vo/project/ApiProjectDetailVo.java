package com.fugary.simple.api.web.vo.project;

import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApiProjectDetailVo extends ApiProject {

    private List<ApiProjectInfo> infoList = new ArrayList<>();
    private List<ApiFolder> folders = new ArrayList<>();
    private List<ApiDoc> docs = new ArrayList<>();
    private List<ApiProjectShareVo> shares = new ArrayList<>();
    private List<ApiProjectTask> tasks = new ArrayList<>();
    private ApiGroupVo apiGroup;
}
