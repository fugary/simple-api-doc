package com.fugary.simple.api.utils.exports;

import com.fugary.simple.api.utils.SchemaJsonUtils;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.media.Schema;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Create date 2025/7/8<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiSchemaContentUtils {
    /**
     * 是否相同
     *
     * @param savedComponentSchemaContent
     * @param componentSchemaContent
     * @param isV31
     * @return
     */
    public static boolean isSameSchemaContent(String savedComponentSchemaContent, String componentSchemaContent, boolean isV31) {
        Schema<?> savedSchema = SchemaJsonUtils.fromJson(savedComponentSchemaContent, Schema.class, isV31);
        Schema<?> newSchema = SchemaJsonUtils.fromJson(componentSchemaContent, Schema.class, isV31);
        return Objects.equals(savedSchema, newSchema);
    }

    /**
     * 合并SchemaContent数据
     *
     * @param savedComponentSchemaContent
     * @param componentSchemaContent
     * @param isV31
     * @return
     */
    public static String mergeComponentSchemaContent(String savedComponentSchemaContent, String componentSchemaContent, boolean isV31) {
        Schema<?> savedSchema = SchemaJsonUtils.fromJson(savedComponentSchemaContent, Schema.class, isV31);
        Schema<?> newSchema = SchemaJsonUtils.fromJson(componentSchemaContent, Schema.class, isV31);
        mergeSchema(savedSchema, newSchema);
        return SchemaJsonUtils.toJson(savedSchema, isV31);
    }

    /**
     * 合并schema，优先长度长的数据
     *
     * @param savedSchema
     * @param newSchema
     */
    public static void mergeSchema(Schema<?> savedSchema, Schema<?> newSchema) {
        // properties
        if (savedSchema.getProperties() == null) {
            savedSchema.setProperties(newSchema.getProperties());
        } else if (newSchema.getProperties() != null) {
            for (Map.Entry<String, Schema> entry : newSchema.getProperties().entrySet()) {
                String propName = entry.getKey();
                Schema<?> newPropSchema = entry.getValue();
                Schema<?> savedPropSchema = (Schema<?>) savedSchema.getProperties().get(propName);

                if (savedPropSchema != null) {
                    // 合并属性
                    mergeStringField(savedPropSchema::getDescription, savedPropSchema::setDescription, newPropSchema.getDescription());
                    mergeStringField(savedPropSchema::getTitle, savedPropSchema::setTitle, newPropSchema.getTitle());
                    mergeStringField(savedPropSchema::getType, savedPropSchema::setType, newPropSchema.getType());

                    // example、default 覆盖式（如果新值不为空就覆盖）
                    if (newPropSchema.getExample() != null) {
                        savedPropSchema.setExample(newPropSchema.getExample());
                    }
                    if (newPropSchema.getDefault() != null) {
                        savedPropSchema.setDefault(newPropSchema.getDefault());
                    }

                    // externalDocs
                    mergeExternalDocs(savedPropSchema.getExternalDocs(), newPropSchema.getExternalDocs(), savedPropSchema);
                } else {
                    // 新增属性
                    savedSchema.getProperties().put(propName, newPropSchema);
                }
            }
        }

        // 顶层字段
        mergeStringField(savedSchema::getTitle, savedSchema::setTitle, newSchema.getTitle());
        mergeStringField(savedSchema::getDescription, savedSchema::setDescription, newSchema.getDescription());
        mergeStringField(savedSchema::getType, savedSchema::setType, newSchema.getType());

        if (newSchema.getExample() != null) {
            savedSchema.setExample(newSchema.getExample());
        }
        if (newSchema.getDefault() != null) {
            savedSchema.setDefault(newSchema.getDefault());
        }

        // required 合并去重
        mergeRequired(savedSchema, newSchema);

        // externalDocs
        mergeExternalDocs(savedSchema.getExternalDocs(), newSchema.getExternalDocs(), savedSchema);
    }

    private static void mergeRequired(Schema<?> savedSchema, Schema<?> newSchema) {
        Set<String> mergedRequired = new LinkedHashSet<>();
        if (savedSchema.getRequired() != null) {
            mergedRequired.addAll(savedSchema.getRequired());
        }
        if (newSchema.getRequired() != null) {
            mergedRequired.addAll(newSchema.getRequired());
        }
        if (!mergedRequired.isEmpty()) {
            savedSchema.setRequired(new ArrayList<>(mergedRequired));
        }
    }

    private static void mergeExternalDocs(ExternalDocumentation savedDocs, ExternalDocumentation newDocs, Schema<?> targetSchema) {
        if (savedDocs == null && newDocs != null) {
            targetSchema.setExternalDocs(newDocs);
        } else if (savedDocs != null && newDocs != null) {
            mergeStringField(savedDocs::getDescription, savedDocs::setDescription, newDocs.getDescription());
            mergeStringField(savedDocs::getUrl, savedDocs::setUrl, newDocs.getUrl());
        }
    }

    /**
     * 字符串字段 merge 策略：
     * - 如果 savedValue 为空，取 newValue
     * - 如果 newValue 不为空 且 长度 > savedValue，取 newValue
     * - 否则保留 savedValue
     */
    private static void mergeStringField(Supplier<String> getter, Consumer<String> setter, String newValue) {
        String savedValue = getter.get();
        if (savedValue == null) {
            setter.accept(newValue);
        } else if (newValue != null && newValue.length() > savedValue.length()) {
            setter.accept(newValue);
        }
    }
}
