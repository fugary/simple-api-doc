package com.fugary.simple.api.web.vo.query;

import lombok.Data;

/**
 * Create date 2024/11/11<br>
 *
 * @author gary.fu
 */
@Data
public class ApiDocHistoryQueryVo extends SimpleQueryVo {
    private Integer docId;
    private Integer version;
}
