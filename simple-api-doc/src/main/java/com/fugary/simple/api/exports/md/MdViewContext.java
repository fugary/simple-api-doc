package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;
import io.swagger.v3.oas.models.media.Schema;
@Data
public class MdViewContext implements Serializable {

    public MdViewContext(ApiDocDetailVo apiDocDetail) {
        this.apiDocDetail = apiDocDetail;
    }

    public MdViewContext() {
    }

    private ApiDocDetailVo apiDocDetail;
    private boolean generateComponents = true;
    private Map<String, Schema<?>> schemasMap;
}
