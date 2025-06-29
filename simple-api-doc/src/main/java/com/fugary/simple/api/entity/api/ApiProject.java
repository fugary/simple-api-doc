package com.fugary.simple.api.entity.api;

import lombok.Data;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProject extends ModelBase {

    private static final long serialVersionUID = -8012815542682860804L;
    private String userName;
    private String groupCode;
    private String projectCode;
    private Boolean privateFlag = true;
    private String projectName;
    private String apiVersion;
    private String envContent;
    private String iconUrl;
    private String description;
}
