package com.fugary.simple.api.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.entity.api.ApiLog;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.AiService;
import com.fugary.simple.api.service.apidoc.ApiLogService;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import com.fugary.simple.api.tasks.GenericCronTask;
import com.fugary.simple.api.tasks.GenericIntervalTask;
import com.fugary.simple.api.tasks.ProjectAutoImportInvoker;
import com.fugary.simple.api.tasks.SimpleTaskWrapper;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.Date;
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
    private ApiLogService apiLogService;

    @Autowired
    private SimpleApiConfigProperties simpleApiConfigProperties;

    @Autowired
    private AiConfigProperties aiConfigProperties;

    @Autowired
    private AiCacheMapper aiCacheMapper;

    @Lazy
    @Autowired
    private AiService aiService;

    /**
     * 自定义池
     *
     * @return
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
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
            if (simpleApiConfigProperties.getApiLogRetentionDays() > 0) {
                scheduledTaskRegistrar.addCronTask(new GenericCronTask<>(
                        new SimpleTaskWrapper<>("api-log-cleanup", "API log cleanup",
                                SecurityUtils.ADMIN_USER),
                        this::cleanupExpiredApiLogs, "0 0 3 * * ?"));
            }
            if (aiService.isEnabled() && simpleApiConfigProperties.getAiCacheRetentionDays() > 0) {
                scheduledTaskRegistrar.addCronTask(new GenericCronTask<>(
                        new SimpleTaskWrapper<>("ai-cache-cleanup", "AI cache cleanup",
                                SecurityUtils.ADMIN_USER),
                        this::cleanupExpiredAiCache, "0 0 4 * * ?"));
            }
            if (aiService.isEnabled()) {
                scheduledTaskRegistrar.addFixedRateTask(new GenericIntervalTask<>(
                        new SimpleTaskWrapper<>("ai-processing-cleanup", "AI processing cleanup",
                                SecurityUtils.ADMIN_USER),
                        this::cleanupProcessingAiCache, 5 * 60 * 1000L));
            }
        }
        return scheduledTaskRegistrar;
    }

    private void cleanupExpiredApiLogs() {
        int retentionDays = simpleApiConfigProperties.getApiLogRetentionDays();
        Date expiredDate = DateUtils.addDays(new Date(), -retentionDays);
        apiLogService.remove(Wrappers.<ApiLog>query().lt("create_date", expiredDate));
        log.info("API log cleanup completed, retention days: {}, expired date: {}", retentionDays, expiredDate);
    }

    private void cleanupExpiredAiCache() {
        int retentionDays = simpleApiConfigProperties.getAiCacheRetentionDays();
        Date aiCacheExpiredDate = DateUtils.addDays(new Date(), -retentionDays);
        aiCacheMapper.delete(Wrappers.<AiCache>query().lt("created_at", aiCacheExpiredDate));
        log.info("AI Cache cleanup completed, retention days: {}, expired date: {}", retentionDays, aiCacheExpiredDate);
    }

    private void cleanupProcessingAiCache() {
        long timeoutMs = aiConfigProperties.getTimeout() + 60000L; // 超时时间 + 1 分钟
        Date processingTimeoutDate = new Date(System.currentTimeMillis() - timeoutMs);
        int deletedProcessing = aiCacheMapper.delete(Wrappers.<AiCache>query()
                .eq("status", 0)
                .lt("created_at", processingTimeoutDate));
        if (deletedProcessing > 0) {
            log.info("Cleaned up {} blocked AI cache processing tasks older than: {}", deletedProcessing, processingTimeoutDate);
        }
    }
}
