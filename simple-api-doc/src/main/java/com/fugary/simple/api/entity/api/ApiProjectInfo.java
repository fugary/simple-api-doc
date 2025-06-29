package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fugary.simple.api.contants.ApiDocConstants;
import lombok.Data;

/**
 * Created on 2024/7/31 11:39 .<br>
 *
 * @author gary.fu
 */
@Data
@TableName(excludeProperty = ApiDocConstants.STATUS_KEY)
public class ApiProjectInfo extends ModelBase {

    private static final long serialVersionUID = 8517817442841283836L;
    private Integer projectId;
    private Integer folderId;
    private String importType;
    private String sourceType;
    private String url;
    private String fileName;
    private String envContent;
    private String authType;
    private String authContent;
    private String version;
    private String oasVersion;
    private String specVersion;
    private Boolean defaultFlag;
}
