package com.fugary.simple.api.web.vo.query.ai;

import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author gary.fu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiConfigQueryVo extends SimpleQueryVo {
    private String configName;
    private String provider;
    private Integer status;
    private Integer isDefault;
    private Integer modifyFrom;
    private String defaultModel;
}
