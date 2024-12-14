package com.fugary.simple.api.exports;

import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;

/**
 * Create date 2024/12/13<br>
 *
 * @author gary.fu
 */
public interface ApiDocViewGenerator {
    /**
     * 将API数据转成显示的格式
     *
     * @param apiDocDetail
     * @return
     */
    String generate(ApiDocDetailVo apiDocDetail);
}
