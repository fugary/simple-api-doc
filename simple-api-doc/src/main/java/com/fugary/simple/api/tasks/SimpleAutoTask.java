package com.fugary.simple.api.tasks;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
public interface SimpleAutoTask<T> {
    /**
     * 获取任务ID
     *
     * @return
     */
    default String getTaskId() {
        return getTaskWrapper().getTaskId();
    }

    /**
     * 获取任务详细数据
     *
     * @return
     */
    SimpleTaskWrapper<T> getTaskWrapper();

    /**
     * 立即触发
     */
    void triggerNow();
}
