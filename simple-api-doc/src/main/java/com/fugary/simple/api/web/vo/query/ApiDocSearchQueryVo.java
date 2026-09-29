package com.fugary.simple.api.web.vo.query;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class ApiDocSearchQueryVo extends SimpleQueryVo {
    @Min(1)
    private Integer projectId;
    @Size(max = 200)
    private String keyword;
    @Size(max = 200)
    private String docName;
    @Size(max = 1000)
    private String url;
    @Size(max = 500)
    private String content;
    @Pattern(regexp = "api|md")
    private String docType;
    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|TRACE")
    private String method;
}
