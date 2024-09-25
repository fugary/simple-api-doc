package com.fugary.simple.api.entity.api;

import lombok.Data;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ApiDoc extends ModelBase {

    private static final long serialVersionUID = -8358397569699746687L;
    private Integer projectId;
    private Integer folderId;
    private String docType;
    private String docName;
    private String docContent;
    private String method;
    private String url;
    private String operationId;
    private String summary;
    private String description;
}
