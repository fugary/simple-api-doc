package com.fugary.simple.api.event.log;

import com.fugary.simple.api.service.apidoc.ApiLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Create date 2024/11/14<br>
 *
 * @author gary.fu
 */
@Slf4j
@Component
public class OperationLogEventListener implements ApplicationListener<OperationLogEvent> {

    @Autowired
    private ApiLogService apiLogService;

    @Async
    @Override
    public void onApplicationEvent(OperationLogEvent event) {
        try {
            log.info("{}", event.getApiLog());
            apiLogService.save(event.getApiLog());
        } catch (Exception e) {
            log.error("操作日志保存失败", e);
        }
    }
}
