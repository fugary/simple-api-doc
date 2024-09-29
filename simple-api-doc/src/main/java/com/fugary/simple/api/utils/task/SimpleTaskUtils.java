package com.fugary.simple.api.utils.task;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.tasks.SimpleTaskWrapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleTaskUtils {

    /**
     * 包装任务执行
     *
     * @param projectTaskWrapper
     * @param runnable
     */
    public static void executeTask(SimpleTaskWrapper<?> projectTaskWrapper, Runnable runnable) {
        long start = System.currentTimeMillis();
        try {
            log.info("开始任务:{}", projectTaskWrapper.getTaskName());
            projectTaskWrapper.setRunningStatus(ApiDocConstants.TASK_STATUS_RUNNING);
            runnable.run();
            log.info("结束任务:{}，耗时：{}", projectTaskWrapper.getTaskName(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("任务执行异常", e);
            projectTaskWrapper.setRunningStatus(ApiDocConstants.TASK_STATUS_ERROR);
        } finally {
            projectTaskWrapper.setRunningStatus(ApiDocConstants.TASK_STATUS_DONE);
        }
    }

}
