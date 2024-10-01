package com.fugary.simple.api.web.vo.project;

import lombok.Data;

import java.io.Serializable;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProjectShareVo implements Serializable {
    private static final long serialVersionUID = 6390677708004409880L;
    private String shareId;
    private String shareName;
    private Boolean exportEnabled;
    private Boolean debugEnabled;
    private String envContent;
    private boolean needPassword;
    private boolean expired;
    private String shareToken;
}
