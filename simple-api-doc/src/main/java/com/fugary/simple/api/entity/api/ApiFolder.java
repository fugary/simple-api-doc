package com.fugary.simple.api.entity.api;

import lombok.Data;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ApiFolder extends ModelBase {

    private static final long serialVersionUID = 9215758244413506093L;
    private Integer projectId;
    private Integer sortId;
    private String folderName;
    private Boolean rootFlag;
    private Integer parentId;
    private String description;
}
