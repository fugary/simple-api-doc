package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fugary.simple.api.contants.ApiDocConstants;
import lombok.Data;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
@TableName(excludeProperty = ApiDocConstants.STATUS_KEY)
public class ApiDocSchema extends ModelBase {

    private static final long serialVersionUID = -9154956851578764749L;
    private Integer docId;
    private String bodyType;
    private String schemaContent;
    private String schemaName;
    private String schemaKey;
    private Integer statusCode;
    private String examples;
    private String contentType;
    private String description;
}
