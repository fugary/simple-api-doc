package com.fugary.simple.api.web.vo.task;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Data
public class SimpleTaskVo implements Serializable {
    private String userName;
    private Integer projectId;
    private String projectName;
    private Integer tid;
    private String taskId;
    private String taskName;
    private String type;
    private String taskStatus;
    private Long triggerDelay;
    private Long triggerRate;
    private String cron;
    private Date lastExecDate;
    private String description;
}
