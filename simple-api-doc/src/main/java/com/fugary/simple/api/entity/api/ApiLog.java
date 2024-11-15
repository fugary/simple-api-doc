package com.fugary.simple.api.entity.api;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Builder
@Data
public class ApiLog implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String creator;
    private Date createDate;
    private String userName;
    private String projectId;
    private String logName;
    private String dataId;
    private String logMessage;
    private String logType;
    private String taskType;
    private String logResult;
    private String logData;
    private String ipAddress;
    private Long logTime;
    private String exceptions;
    private String extend1;
    private String extend2;
}
