package com.fugary.simple.api.web.vo.exports;

import com.fugary.simple.api.entity.api.ApiDoc;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Data
public class ExportApiDocVo extends ApiDoc {

    private static final long serialVersionUID = -8612077469215078772L;
    private ExportApiDocSchemaVo parametersSchema;
    private List<ExportApiDocSchemaVo> requestsSchemas = new ArrayList<>();
    private List<ExportApiDocSchemaVo> responsesSchemas = new ArrayList<>();
}
