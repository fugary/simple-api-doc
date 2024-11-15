package com.fugary.simple.api.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.tasks.ProjectAutoImportInvoker;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.List;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@Configuration
public class TaskScheduleConfig {

    @Autowired
    private ApiProjectTaskService apiProjectTaskService;

    @Autowired
    private ProjectAutoImportInvoker projectAutoImportInvoker;

    @Autowired
    private SimpleApiConfigProperties simpleApiConfigProperties;

    /**
     * 自定义池
     *
     * @return
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(simpleApiConfigProperties.getTaskPoolSize());
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.setThreadNamePrefix("simple-task-");
        return taskScheduler;
    }

    @Bean
    public ScheduledTaskRegistrar scheduledTaskRegistrar() {
        ScheduledTaskRegistrar scheduledTaskRegistrar = new ScheduledTaskRegistrar();
        scheduledTaskRegistrar.setTaskScheduler(taskScheduler());
        if (simpleApiConfigProperties.isTaskEnabled()) {
            List<ApiProjectTask> projectTasks = apiProjectTaskService.list(Wrappers.lambdaQuery(ApiProjectTask.class)
                    .eq(ApiProjectTask::getTaskType, ApiDocConstants.PROJECT_TASK_TYPE_AUTO)
                    .eq(ApiProjectTask::getStatus, ApiDocConstants.STATUS_ENABLED));
            if (CollectionUtils.isEmpty(projectTasks)) {
                log.info("没有需要自动导入的项目任务");
            }
            for (ApiProjectTask projectTask : projectTasks) {
                if (projectTask.getScheduleRate() != null) {
                    scheduledTaskRegistrar.addFixedRateTask((IntervalTask) SimpleTaskUtils.projectTask2SimpleTask(projectTask,
                            projectAutoImportInvoker, simpleApiConfigProperties));
                }
            }
//            scheduledTaskRegistrar.addFixedRateTask(new SimpleTestTask(
//                    new SimpleTaskWrapper<>("simple-test", "简单测试任务", null),
//                    () -> log.info("简单测试....")));
        }
        return scheduledTaskRegistrar;
    }
}
