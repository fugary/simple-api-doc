package com.fugary.simple.api.event.log;

import com.fugary.simple.api.entity.api.ApiLog;
import org.springframework.context.ApplicationEvent;

/**
 * Create date 2024/11/14<br>
 *
 * @author gary.fu
 */
public class OperationLogEvent extends ApplicationEvent {

    public OperationLogEvent(ApiLog source) {
        super(source);
    }

    public ApiLog getApiLog() {
        return (ApiLog) getSource();
    }
}
