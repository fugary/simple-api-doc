package com.fugary.simple.api.entity.api;

import lombok.Data;

import java.util.Date;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProjectTask extends ModelBase {

    private static final long serialVersionUID = -4123706039177520354L;
    private Integer projectId;
    private String taskType;
    private String taskName;
    private String sourceType;
    private String sourceUrl;
    private Integer scheduleRate; // 单位秒
    private Integer toFolder;
    private Integer overwriteMode;
    private String authType;
    private String authContent;
    private Date execDate;
}
