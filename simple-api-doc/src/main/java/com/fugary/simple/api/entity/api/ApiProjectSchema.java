package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Created on 2024/7/31 11:39 .<br>
 *
 * @author gary.fu
 */
@Data
@TableName(excludeProperty = "status")
public class ApiProjectSchema extends ModelBase {

    private static final long serialVersionUID = 8517817442841283836L;
    private Integer projectId;
    private String importType;
    private String sourceType;
    private String url;
    private String fileName;
    private String contentType;
    private String envContent;
    private String authType;
    private String authContent;
    private String version;
    private String oasVersion;
    private String specVersion;
}
