package com.fugary.simple.api.exports;

import com.fugary.simple.api.exports.md.MdViewContext;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.models.media.Schema;

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

    default Map<String, Schema<?>> sortSchemasMap(Map<String, Schema<?>> schemasMap, List<String> directSchemaNames) {
        return schemasMap;
    }
}
