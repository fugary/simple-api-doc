package com.fugary.simple.api.web.vo.imports;

import lombok.Data;

/**
 * 导入方式新建项目
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProjectImportVo extends UrlWithAuthVo {

    private static final long serialVersionUID = -8016717000143824799L;
    private String projectName;
    private String iconUrl;
    private String importType;
    private String sourceType;
    private String groupCode;
}
