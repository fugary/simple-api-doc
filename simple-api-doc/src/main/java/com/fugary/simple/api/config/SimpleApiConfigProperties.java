package com.fugary.simple.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created on 2020/5/5 20:43 .<br>
 *
 * @author gary.fu
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "simple.api")
public class SimpleApiConfigProperties {

    private String projectVersion;

    private String jwtPassword = "";

    private Integer jwtExpire = 7;
    /**
     * 上传大小
     */
    private long maxUploadSize = 10 * 1024 * 1024;
    /**
     * 是否开启任务
     */
    private boolean taskEnabled = true;
    /**
     * 任务池大小
     */
    private int taskPoolSize = 10;

    /**
     * 记录数据库日志开关
     */
    private boolean apiLogEnabled = true;
}
