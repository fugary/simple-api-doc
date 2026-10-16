package com.fugary.simple.api.exports.md;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiDocFreemarkerUtilsTest {
    private ApiDocFreemarkerUtils utils() {
        ApiDocFreemarkerUtils utils = new ApiDocFreemarkerUtils();
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        utils.setMessageSource(messages);
        return utils;
    }

    @Test
    void fixedColumnsPreserveDefaultsAndEscapeCells() {
        Schema<Object> enabled = new Schema<>().type("boolean").description("first | second\nnext");
        enabled.setDefault(false);
        enabled.setExample(false);
        Schema<Object> count = new Schema<>().type("integer");
        count.setDefault(0);
        Schema<Object> model = new Schema<>().type("object");
        model.addProperty("enabled", enabled);
        model.addProperty("count", count);
        model.addRequiredItem("enabled");
        String table = utils().schemaToTable(model);
        assertTrue(table.startsWith("| api.label.fieldName | api.label.fieldType | api.label.paramDesc | api.label.defaultValue | api.label.fieldRequired | api.label.remark |"));
        assertTrue(table.contains("| `false` | `Y` |"));
        assertTrue(table.contains("| `0` | N |"));
        assertTrue(table.contains("&#124;"));
        for (String line : table.split("\\R")) {
            assertEquals(7, line.chars().filter(c -> c == '|').count());
        }
    }

    @Test
    void parameterLocationAndEnumsRemainInRemarks() {
        Schema<String> schema = new Schema<String>().type("string");
        schema.setEnum(List.of("a|b", "c"));
        Parameter parameter = new Parameter().name("filter").in("query").schema(schema).required(true);
        String table = utils().parametersToTable(List.of(parameter));
        assertTrue(table.contains("api.label.paramIn: query"));
        assertTrue(table.contains("api.label.enums: `a&#124;b`, `c`"));
        assertEquals(utils().schemaToTable(new Schema<>().addProperty("filter", schema)).split("\\R")[0], table.split("\\R")[0]);
    }
    @Test
    void formatsBelongToTypesAndEnumsOnlyToRemarks() {
        Schema<String> item = new Schema<String>().type("string");
        item.setEnum(List.of("0", "1"));
        Schema<?> payments = new Schema<>().type("array").items(item);
        Schema<?> id = new Schema<>().type("integer").format("int64");
        Schema<?> date = new Schema<>().type("string").format("date-time");
        Schema<?> model = new Schema<>().type("object");
        model.addProperty("paymentTypes", payments);
        model.addProperty("id", id);
        model.addProperty("date", date);
        ApiDocFreemarkerUtils utils = utils();
        String table = utils.schemaToTable(model);
        assertEquals("array<string>", utils.propertyType(payments, false));
        assertTrue(table.contains("| integer(int64) |"));
        assertTrue(table.contains("| string(date-time) |"));
        assertTrue(table.contains("api.label.itemEnums: `0`, `1`"));
        assertFalse(table.contains("format:"));
        assertFalse(utils.propertyType(item, false).contains("api.label.enums"));
        assertTrue(utils.schemaToTable(item).contains("api.label.enums: `0`, `1`"));
        assertTrue(utils.parametersToTable(List.of(new Parameter().name("id").schema(id)))
                .contains("| integer(int64) |"));
    }
    @Test
    void composedEnumsKeepBranchLocationsWithoutExpandingProperties() {
        ApiDocFreemarkerUtils utils = utils();
        for (String kind : List.of("allOf", "anyOf", "oneOf")) {
            String json = "{\"" + kind + "\":[{\"type\":\"string\",\"enum\":[\"a\"]},"
                    + "{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"b\"]}}]}";
            Schema<?> schema = com.fugary.simple.api.utils.SchemaJsonUtils.fromJson(json, Schema.class, false);
            String table = utils.schemaToTable(new Schema<>().addProperty("choice", schema));
            assertTrue(table.contains("api.label.enums (" + kind + "[1]): `a`"));
            assertTrue(table.contains("api.label.itemEnums (" + kind + "[2].items): `b`"));
        }
        Schema<String> shared = new Schema<String>().type("string");
        shared.setEnum(List.of("shared"));
        Schema<Object> root = new Schema<>();
        root.setOneOf(List.of(shared, shared, root));
        root.addProperty("hidden", shared);
        String remarks = utils.schemaEnumRemarks(root);
        assertTrue(remarks.contains("oneOf[1]"));
        assertTrue(remarks.contains("oneOf[2]"));
        assertFalse(remarks.contains("hidden"));
    }
}
