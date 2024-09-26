package com.fugary.simple.api.web.vo.exports;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fugary.simple.api.entity.api.ApiProjectSchemaDetail;
import lombok.Data;

/**
 * Create date 2024/9/25<br>
 *
 * @author gary.fu
 */
@Data
@TableName(excludeProperty = "status")
public class ExportApiProjectSchemaDetailVo extends ApiProjectSchemaDetail {
}
