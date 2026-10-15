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
}
