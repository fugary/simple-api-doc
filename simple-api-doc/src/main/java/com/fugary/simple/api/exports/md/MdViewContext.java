package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class MdViewContext implements Serializable {

    public MdViewContext(ApiDocDetailVo apiDocDetail) {
        this.apiDocDetail = apiDocDetail;
    }

    public MdViewContext() {
    }

    private ApiDocDetailVo apiDocDetail;
    private List<String> reqOrResNames = new ArrayList<>();
    private boolean generateComponents = true;
}
