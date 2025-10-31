package com.fugary.simple.api.entity.api;

import lombok.Data;

import java.util.Date;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProjectShare extends ModelBase {

    private static final long serialVersionUID = 3454080047647993340L;
    private Integer projectId;
    private String shareId;
    private String shareName;
    private Boolean exportEnabled;
    private Boolean debugEnabled;
    private String defaultTheme;
    private String defaultShowLabel;
    private String envContent;
    private String sharePassword;
    private String shareDocs;
    private Boolean showChildrenLength;
    private Boolean showTreeIcon;
    private String copyRight;
    private String waterMark;
    private Date expireDate;
}
