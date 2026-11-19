package com.fugary.simple.api.exports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.utils.exports.ApiSchemaContentUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoDetailVo;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证 ApiSchemaContentUtils 及 Schema 保留与对比逻辑
 */
public class ApiSchemaContentUtilsTest {

    private static final String NON_STANDARD_SCHEMA = "{\n" +
            "  \"type\": \"object\",\n" +
            "  \"title\": \"SearchRoomPricesParam\",\n" +
            "  \"properties\": {\n" +
            "    \"cityCode\": {\n" +
            "      \"type\": \"string\",\n" +
            "      \"description\": \"城市代码\"\n" +
            "    },\n" +
            "    \"prePolicySelectData\": {\n" +
            "      \"type\": \"prepolicyselectdatadto\",\n" +
            "      \"description\": \"预填写MI信息\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

    @Test
    public void testRetainComponentSchemaDescription_PreservesNonStandardTypeWhenIdentical() throws Exception {
        String result = ApiSchemaContentUtils.retainComponentSchemaDescription(NON_STANDARD_SCHEMA, NON_STANDARD_SCHEMA, false);
        Assertions.assertEquals(NON_STANDARD_SCHEMA, result);
        JsonNode node = JsonUtils.getMapper().readTree(result);
        Assertions.assertTrue(node.path("properties").has("prePolicySelectData"), "不应丢失非标类型属性 prePolicySelectData");
        Assertions.assertEquals("prepolicyselectdatadto", node.path("properties").path("prePolicySelectData").path("type").asText());
    }

    @Test
    public void testRetainComponentSchemaDescription_RetainsCustomDescriptionWithoutDroppingFields() throws Exception {
        String savedSchema = "{\n" +
            "  \"type\": \"object\",\n" +
            "  \"title\": \"SearchRoomPricesParam\",\n" +
            "  \"description\": \"用户自定义的模型长描述信息\",\n" +
            "  \"properties\": {\n" +
            "    \"cityCode\": {\n" +
            "      \"type\": \"string\",\n" +
            "      \"description\": \"这是用户修改后更详细的城市代码说明\"\n" +
            "    },\n" +
            "    \"prePolicySelectData\": {\n" +
            "      \"type\": \"prepolicyselectdatadto\",\n" +
            "      \"description\": \"预填写MI信息（包含自定义说明）\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

        String result = ApiSchemaContentUtils.retainComponentSchemaDescription(savedSchema, NON_STANDARD_SCHEMA, false);
        JsonNode node = JsonUtils.getMapper().readTree(result);

        Assertions.assertTrue(node.path("properties").has("prePolicySelectData"), "合并描述时不应丢失非标类型属性");
        Assertions.assertEquals("prepolicyselectdatadto", node.path("properties").path("prePolicySelectData").path("type").asText());
        Assertions.assertEquals("预填写MI信息（包含自定义说明）", node.path("properties").path("prePolicySelectData").path("description").asText());
        Assertions.assertEquals("这是用户修改后更详细的城市代码说明", node.path("properties").path("cityCode").path("description").asText());
        Assertions.assertEquals("用户自定义的模型长描述信息", node.path("description").asText());
    }

    @Test
    public void testMergeComponentSchemaContent_ReturnsSavedWhenIdentical() {
        String result = ApiSchemaContentUtils.mergeComponentSchemaContent(NON_STANDARD_SCHEMA, NON_STANDARD_SCHEMA, false);
        Assertions.assertEquals(NON_STANDARD_SCHEMA, result);
    }

    @Test
    public void testProcessProjectInfoDetail_NoChangeWhenSameContentWithNonStandardType() {
        ApiProjectInfoDetail saved = new ApiProjectInfoDetail();
        saved.setId(100);
        saved.setProjectId(1);
        saved.setInfoId(1);
        saved.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT);
        saved.setSchemaName("SearchRoomPricesParam");
        saved.setSchemaContent(NON_STANDARD_SCHEMA);
        saved.setVersion(1);
        saved.setStatus(null); // 数据库实体 status 为 null

        ExportApiProjectInfoDetailVo incoming = new ExportApiProjectInfoDetailVo();
        incoming.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT);
        incoming.setSchemaName("SearchRoomPricesParam");
        incoming.setSchemaContent(NON_STANDARD_SCHEMA);
        incoming.setStatus(1); // 前端 Vo 可能有默认 status
        incoming.setProjectId(1);
        incoming.setInfoId(1);

        Map<String, ApiProjectInfoDetail> detailsMap = new HashMap<>();
        detailsMap.put(ApiDocParseUtils.getProjectInfoDetailKey(incoming), saved);

        Pair<ExportApiProjectInfoDetailVo, ApiProjectInfoDetail> result =
                ApiDocParseUtils.processProjectInfoDetail(detailsMap, incoming, false);

        Assertions.assertNull(result.getLeft(), "相同内容重复导入时应判定为未变更（null）");
        Assertions.assertNull(result.getRight(), "相同内容重复导入时应判定为未变更（null）");
    }
}
