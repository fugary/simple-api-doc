package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fugary.simple.api.contants.ApiDocConstants;
import lombok.Data;

/**
 * Create date 2024/11/8<br>
 *
 * @author gary.fu
 */
@Data
@TableName(excludeProperty = {ApiDocConstants.MODIFIER_KEY, ApiDocConstants.MODIFY_DATE_KEY})
public class ApiDocHistory extends ModelBase {

    private static final long serialVersionUID = 4847408942480677814L;
    private Integer docId;
    private Integer sortId;
    private String docType;
    private String docName;
    private String docKey;
    private String docContent;
    private String description;
    private Integer docVersion;
}
