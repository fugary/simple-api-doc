package com.fugary.simple.api.exports;

import com.fugary.simple.api.exports.md.MdViewContext;

/**
 * Create date 2024/12/13<br>
 *
 * @author gary.fu
 */
public interface ApiDocViewGenerator {
    /**
     * 将API数据转成显示的格式
     *
     * @param context
     * @return
     */
    String generate(MdViewContext context);
}
