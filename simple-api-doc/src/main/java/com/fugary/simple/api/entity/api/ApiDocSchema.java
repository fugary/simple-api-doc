package com.fugary.simple.api.entity.api;

import lombok.Data;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ApiDocSchema extends ModelBase {

    private static final long serialVersionUID = -9154956851578764749L;
    private Integer docId;
    private String bodyType;
    private String schemaContent;
    private String contentType;
    private String description;
}
