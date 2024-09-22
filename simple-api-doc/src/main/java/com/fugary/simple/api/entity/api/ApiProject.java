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
    private String projectCode;
    private Boolean privateFlag;
    private String projectName;
    private String description;
}
