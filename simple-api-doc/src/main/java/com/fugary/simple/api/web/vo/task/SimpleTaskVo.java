package com.fugary.simple.api.web.vo.task;

import lombok.Data;

import java.io.Serializable;

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
    private String taskId;
    private String taskName;
    private String taskStatus;
}
