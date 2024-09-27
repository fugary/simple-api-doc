package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fugary.simple.api.contants.ApiDocConstants;
import lombok.Data;

/**
 * Create date 2024/9/25<br>
 *
 * @author gary.fu
 */
@Data
@TableName(excludeProperty = ApiDocConstants.STATUS_KEY)
public class ApiProjectInfoDetail extends ModelBase {

    private static final long serialVersionUID = 593626013543617186L;
    private Integer projectId;
    private Integer infoId;
    private String bodyType;
    private String schemaContent;
    private String schemaName;
    private String schemaKey;
    private String contentType;
    private String description;
}
