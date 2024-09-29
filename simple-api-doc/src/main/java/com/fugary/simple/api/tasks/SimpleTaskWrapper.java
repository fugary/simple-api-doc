package com.fugary.simple.api.tasks;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Create date 2024/9/29<br>
 *
 * @author gary.fu
 */
@RequiredArgsConstructor
@Data
public class SimpleTaskWrapper<T>{

    private String runningStatus;

    private final String taskId;

    private final String taskName;

    private final T data;

}
