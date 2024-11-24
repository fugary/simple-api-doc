package com.fugary.simple.api.entity.api;

import lombok.Data;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@Data
public class ApiGroup extends ModelBase {

    private static final long serialVersionUID = -5192258938979353356L;
    private String groupCode;
    private String groupName;
    private String userName;
    private String description;
}
