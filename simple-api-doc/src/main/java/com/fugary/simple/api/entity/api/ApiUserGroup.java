package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fugary.simple.api.contants.ApiDocConstants;
import lombok.Data;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@Data
@TableName(excludeProperty = {ApiDocConstants.STATUS_KEY, ApiDocConstants.MODIFIER_KEY, ApiDocConstants.MODIFY_DATE_KEY})
public class ApiUserGroup extends ModelBase {
    private Integer userId;
    private String groupCode;
    private String authorities;
}
