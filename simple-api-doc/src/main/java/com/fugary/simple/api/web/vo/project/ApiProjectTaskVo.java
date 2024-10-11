package com.fugary.simple.api.web.vo.project;

import com.fugary.simple.api.entity.api.ApiProjectTask;
import lombok.Data;

/**
 * Create date 2024/10/11<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProjectTaskVo extends ApiProjectTask {

    private static final long serialVersionUID = -572378459743753694L;
    private String taskId;
    private String scheduleStatus;
    private String taskStatus;
}
