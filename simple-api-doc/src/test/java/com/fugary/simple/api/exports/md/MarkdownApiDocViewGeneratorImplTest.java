package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import freemarker.template.Configuration;
import freemarker.template.TemplateMethodModelEx;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MarkdownApiDocViewGeneratorImplTest {

    private MarkdownApiDocViewGeneratorImpl generator;

    @BeforeEach
    void setup() throws Exception {
        ApiDocFreemarkerUtils utils = new ApiDocFreemarkerUtils();
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        utils.setMessageSource(messages);
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassForTemplateLoading(getClass(), "/templates");
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setSharedVariable("utils", utils);
        configuration.setSharedVariable("message", (TemplateMethodModelEx) arguments -> arguments.get(0).toString());
        generator = new MarkdownApiDocViewGeneratorImpl();
        generator.setApiDocFreemarkerUtils(utils);
        generator.setFreemarkerConfig(configuration);
    }

    @Test
    void testSortSchemasMapPriority() {
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

    @Test
    void testGenerateWithInlineResponseSchema() {
        ApiDocDetailVo doc = createDoc();
        doc.setResponsesSchemas(List.of(bodySchema("PersonResponse", objectSchema("name"))));
        assertModel(generator.generate(new MdViewContext(doc)), "PersonResponse", "name");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void testInlineRequestAndResponseArrays(int dimensions) {
        String schema = objectSchema("name");
        for (int i = 0; i < dimensions; i++) {
            schema = "{\"type\":\"array\",\"items\":" + schema + "}";
        }
        ApiDocDetailVo doc = createDoc();
        doc.setRequestsSchemas(List.of(bodySchema(null, schema)));
        doc.setResponsesSchemas(List.of(bodySchema("200", schema)));
        String markdown = generator.generate(new MdViewContext(doc));
        assertModel(markdown, "_request", "name");
        assertModel(markdown, "_response_200", "name");
        if (dimensions > 0) {
            Assertions.assertTrue(markdown.contains("array&lt;"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"allOf", "anyOf", "oneOf"})
    void testComposedInlineSchemasKeepEveryBranch(String composition) {
        ApiDocDetailVo doc = createDoc();
        String schema = "{\"" + composition + "\":[" + objectSchema("first") + "," + objectSchema("second") + "]}";
        doc.setResponsesSchemas(List.of(bodySchema("Result", schema)));
        String markdown = generator.generate(new MdViewContext(doc));
        assertModel(markdown, "Result", "first");
        Assertions.assertTrue(markdown.contains("**`second`**"), "组合模型的第二个分支不能丢失");
        Assertions.assertFalse(markdown.contains("Result." + composition), "组合模型的分支不应该拆成伪模型");
    }

    @Test
    void testComposedInlineSchemasNestedObjects() {
        ApiDocDetailVo doc = createDoc();
        String schema = "{\"allOf\":["
                + "{\"type\":\"object\",\"properties\":{\"head\":{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"integer\"}}}}},"
                + "{\"type\":\"object\",\"properties\":{\"page\":{\"type\":\"object\",\"properties\":{\"size\":{\"type\":\"integer\"}}}}}"
                + "]}";
        doc.setResponsesSchemas(List.of(bodySchema("CustomResponse", schema)));
        String markdown = generator.generate(new MdViewContext(doc));
        assertModel(markdown, "CustomResponse", "head");
        assertModel(markdown, "CustomResponse", "page");
        assertModel(markdown, "CustomResponse.head", "code");
        assertModel(markdown, "CustomResponse.page", "size");
        Assertions.assertFalse(markdown.contains("allOf"), "不应包含 allOf 伪模型");
    }

    @Test
    void testInlineNamesDoNotCollideWithComponentsOrEachOther() {
        ApiDocDetailVo doc = createDoc();
        doc.setRequestsSchemas(List.of(bodySchema("Result", objectSchema("requestField"))));
        doc.setResponsesSchemas(List.of(bodySchema("Result", objectSchema("responseField"))));
        MdViewContext context = new MdViewContext(doc);
        Schema<?> component = new ObjectSchema().name("Result")
                .addProperty("componentField", new Schema<>().type("string"));
        Map<String, Schema<?>> schemas = new LinkedHashMap<>();
        schemas.put("Result", component);
        context.setSchemasMap(schemas);
        String markdown = generator.generate(context);
        Assertions.assertSame(component, schemas.get("Result"));
        Assertions.assertTrue(markdown.contains("**`componentField`**"));
        assertModel(markdown, "Result_2", "requestField");
        assertModel(markdown, "Result_3", "responseField");
    }

    @Test
    void testSharedExportContextRetainsDistinctInlineModels() {
        Map<String, Schema<?>> schemas = new LinkedHashMap<>();
        MdViewContext context = new MdViewContext();
        context.setSchemasMap(schemas);
        context.setGenerateComponents(false);
        for (String field : List.of("first", "second")) {
            ApiDocDetailVo doc = createDoc();
            doc.setResponsesSchemas(List.of(bodySchema("200", objectSchema(field))));
            context.setApiDocDetail(doc);
            generator.generate(context);
        }
        Assertions.assertEquals(2, schemas.size(), "合并导出必须收集每个接口的内联模型");
        Assertions.assertTrue(schemas.get("_response_200").getProperties().containsKey("first"));
        Assertions.assertTrue(schemas.get("_response_200_2").getProperties().containsKey("second"));
    }

    @Test
    void testReferencedComponentKeepsNestedInlineAndRecursiveLinks() {
        ApiDocDetailVo doc = createDoc();
        doc.setResponsesSchemas(List.of(bodySchema("200", "{\"$ref\":\"#/components/schemas/Envelope\"}")));
        Schema<?> item = new ObjectSchema().addProperty("name", new Schema<>().type("string"));
        Schema<?> component = new ObjectSchema().name("Envelope")
                .addProperty("items", new Schema<>().type("array").items(item))
                .addProperty("parent", new Schema<>().$ref("#/components/schemas/Envelope"));
        Map<String, Schema<?>> schemas = new LinkedHashMap<>();
        schemas.put("Envelope", component);
        MdViewContext context = new MdViewContext(doc);
        context.setSchemasMap(schemas);
        String markdown = generator.generate(context);
        assertModel(markdown, "Envelope", "parent");
        assertModel(markdown, "Envelope.items", "name");
        Assertions.assertEquals(2, schemas.size());
        Assertions.assertEquals("#/components/schemas/Envelope", component.getProperties().get("parent").get$ref());
    }

    private ApiDocDetailVo createDoc() {
        ApiDocDetailVo doc = new ApiDocDetailVo();
        doc.setDocName("test");
        doc.setMethod("GET");
        doc.setUrl("/get");
        return doc;
    }

    private ApiProjectInfoDetail bodySchema(String name, String schema) {
        ApiProjectInfoDetail detail = new ApiProjectInfoDetail();
        detail.setSchemaName(name);
        detail.setStatusCode(200);
        detail.setContentType("application/json");
        detail.setSchemaContent("{\"schema\":" + schema + "}");
        return detail;
    }

    private String objectSchema(String field) {
        return "{\"type\":\"object\",\"properties\":{\"" + field + "\":{\"type\":\"string\"}}}";
    }

    private void assertModel(String markdown, String model, String field) {
        Assertions.assertTrue(markdown.contains("href=\"#" + model + "\""), "缺少模型链接: " + model);
        Assertions.assertTrue(markdown.contains("#### " + model), "缺少模型表格: " + model);
        Assertions.assertTrue(markdown.contains("**`" + field + "`**"), "缺少字段: " + field);
    }
}
