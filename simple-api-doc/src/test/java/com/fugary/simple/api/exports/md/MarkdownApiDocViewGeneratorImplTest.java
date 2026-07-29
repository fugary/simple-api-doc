package com.fugary.simple.api.exports.md;

import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MarkdownApiDocViewGeneratorImplTest {

    @Test
    void testSortSchemasMapPriority() {
        MarkdownApiDocViewGeneratorImpl generator = new MarkdownApiDocViewGeneratorImpl();
        generator.setApiDocFreemarkerUtils(new ApiDocFreemarkerUtils());

        Map<String, Schema<?>> schemasMap = new LinkedHashMap<>();

        // 创建各个模型
        Schema<?> userVo = new ObjectSchema().name("UserVo");
        Schema<?> otherDto = new ObjectSchema().name("OtherDto");
        Schema<?> simulateLoginParam = new ObjectSchema().name("SimulateLoginParam");

        Schema<?> clientInfoDto = new ObjectSchema().name("ClientInfoDto");
        Schema<?> resultVo = new ObjectSchema().name("ResultVo");
        resultVo.addProperty("clientInfo", new Schema<>().$ref("#/components/schemas/ClientInfoDto"));

        // 原顺序：UserVo -> OtherDto -> SimulateLoginParam -> ClientInfoDto -> ResultVo
        schemasMap.put("UserVo", userVo);
        schemasMap.put("OtherDto", otherDto);
        schemasMap.put("SimulateLoginParam", simulateLoginParam);
        schemasMap.put("ClientInfoDto", clientInfoDto);
        schemasMap.put("ResultVo", resultVo);

        // 请求模型 SimulateLoginParam
        List<FmApiDocSchema> requestSchemas = new ArrayList<>();
        FmApiDocSchema reqSchema = new FmApiDocSchema();
        reqSchema.setSchema(new Schema<>().$ref("#/components/schemas/SimulateLoginParam"));
        requestSchemas.add(reqSchema);

        // 响应模型 ResultVo
        List<FmApiDocSchema> responseSchemas = new ArrayList<>();
        FmApiDocSchema respSchema = new FmApiDocSchema();
        respSchema.setSchema(new Schema<>().$ref("#/components/schemas/ResultVo"));
        responseSchemas.add(respSchema);

        // 执行排序
        Map<String, Schema<?>> sortedMap = generator.sortSchemasMap(schemasMap, requestSchemas, responseSchemas);

        List<String> keys = new ArrayList<>(sortedMap.keySet());
        Assertions.assertEquals(5, keys.size());
        // 验证顺序：请求模型 -> 响应模型 -> 嵌套模型 -> 其他模型
        Assertions.assertEquals("SimulateLoginParam", keys.get(0));
        Assertions.assertEquals("ResultVo", keys.get(1));
        Assertions.assertEquals("ClientInfoDto", keys.get(2));
        Assertions.assertEquals("UserVo", keys.get(3));
        Assertions.assertEquals("OtherDto", keys.get(4));
    }
}
