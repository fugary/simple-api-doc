package com.fugary.simple.api.web.vo.imports;

import lombok.Data;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ApiProjectTaskImportVo extends ApiProjectImportVo {

    private static final long serialVersionUID = -1692614327176179589L;
    private Integer projectId;
    private String taskType;
    private String taskName;
    private Integer scheduleRate;
    private Integer toFolder;
    private Integer overwriteMode;
}
