package com.fugary.simple.api.tasks;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class ProjectAutoImportTask {

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void importProjects() {
        List<ApiProjectTask> projectTasks = apiProjectTaskService.list(Wrappers.lambdaQuery(ApiProjectTask.class)
                .eq(ApiProjectTask::getTaskType, ApiDocConstants.PROJECT_TASK_TYPE_AUTO)
                .eq(ApiProjectTask::getStatus, ApiDocConstants.STATUS_ENABLED));
        if (CollectionUtils.isEmpty(projectTasks)) {
            log.info("没有需要自动导入的项目");
            return;
        }
        for (ApiProjectTask projectTask : projectTasks) {
            log.info("开始自动导入项目:{}", projectTask.getTaskName());
        }
    }
}
