package com.fugary.simple.api.web.vo.doc;

import lombok.Data;

import java.io.Serializable;

@Data
public class ApiDocSearchResultVo implements Serializable {
    private Integer id;
    private Integer projectId;
    private String projectCode;
    private String projectName;
    private String folderPath;
    private String docName;
    private String docType;
    private String url;
    private String method;
    private Integer status;
    private String snippet;
}
