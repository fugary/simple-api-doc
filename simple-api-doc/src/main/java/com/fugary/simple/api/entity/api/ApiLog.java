package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fugary.simple.api.contants.ApiDocConstants;
import lombok.Data;

@Data
@TableName(excludeProperty = {ApiDocConstants.STATUS_KEY, ApiDocConstants.MODIFIER_KEY, ApiDocConstants.MODIFY_DATE_KEY})
public class ApiLog extends ModelBase {
    private String userName;
    private String projectId;
    private String logName;
    private String dataId;
    private String logMessage;
    private String logType;
    private String taskType;
    private String logResult;
    private String logData;
    private String exceptions;
    private String extend1;
    private String extend2;
    private String creator;
}
