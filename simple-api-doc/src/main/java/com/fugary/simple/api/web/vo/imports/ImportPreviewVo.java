package com.fugary.simple.api.web.vo.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fugary.simple.api.utils.JsonUtils;
import lombok.Data;
import java.util.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** 只读预览响应；不传入正式导入和保存流程。 */
@Data
public class ImportPreviewVo implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private String projectName;
    private String targetFolder;
    private String source;
    private String sourceTitle;
    private String sourceVersion;
    private List<Change> changes = new ArrayList<>();

    public boolean isChanged() {
        return changes.stream().anyMatch(c -> "added".equals(c.status) || "updated".equals(c.status));
    }

    public Change record(String type, String name, String path, String status, Object before, Object after) {
        Change change = new Change();
        change.setType(type);
        change.setName(name);
        change.setPath(path);
        // 文档和模型状态由导入规则给出；未变和锁定项不必生成完整差异。
        if ("unchanged".equals(status) || "locked".equals(status)) {
            change.setStatus(status);
            changes.add(change);
            return change;
        }
        // 比较真实内容后再脱敏展示，避免仅凭据变化时漏报更新。
        boolean sourceContent = "sourceContent".equals(type);
        JsonNode original = sourceContent ? null : normalized(before, type, false, false);
        JsonNode modified = sourceContent ? null : normalized(after, type, false, false);
        change.setStatus(status != null ? status : before == null ? "added"
                : Objects.equals(original, modified) ? "unchanged" : "updated");
        if ("updated".equals(change.getStatus()) && original != null && modified != null) {
            Set<String> fields = new TreeSet<>();
            original.fieldNames().forEachRemaining(fields::add);
            modified.fieldNames().forEachRemaining(fields::add);
            fields.stream().filter(field -> !Objects.equals(original.get(field), modified.get(field)))
                    .forEach(change.getChangedFields()::add);
        }
        change.setFormatOnly("updated".equals(change.getStatus()) && !sourceContent && Objects.equals(original, modified));
        if (!Set.of("unchanged", "locked").contains(change.getStatus())) {
            change.setOriginal(content(original));
            change.setModified(content(modified));
            // 纯空白变化在格式化后不可见，退回保存文本（JSON 字符串中的空白会转义显示）。
            if (change.isFormatOnly() && Objects.equals(change.getOriginal(), change.getModified())) {
                change.setOriginal(normalized(before, type, true, true).toPrettyString());
                change.setModified(normalized(after, type, true, true).toPrettyString());
            }
        }
        changes.add(change);
        return change;
    }

    private static final Set<String> INTERNAL_FIELDS = Set.of("id", "projectId", "infoId", "docId", "folderId",
            "parentId", "creator", "modifier", "createDate", "modifyDate", "modifyFrom",
            "project", "projectInfoDetail", "apiShare", "apiMarkdown", "topLevelFolder", "folderPath");
    private static final Set<String> SECRET_FIELDS = Set.of("authContent", "password", "token", "apiKey",
            "accessToken", "secret", "x-default-auth");

    private static JsonNode normalized(Object value, String type, boolean rawJson, boolean redact) {
        if (value == null) { return null; }
        JsonNode node = JsonUtils.getMapper().valueToTree(value);
        // 来源配置和 Schema 的 status 都不入库；保留文档、目录和项目的真实状态。
        if ("source".equals(type) && node.isObject()) {
            ((ObjectNode) node).remove(Arrays.asList("status", "enabled"));
        }
        clean(node, true, redact, rawJson);
        return node;
    }

    private static String content(JsonNode node) {
        if (node == null) { return ""; }
        clean(node, true, true, false);
        return node.toPrettyString();
    }

    private static void clean(JsonNode node, boolean entity, boolean redact, boolean rawJson) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (entity) {
                object.remove(INTERNAL_FIELDS);
                // ApiProjectInfoDetail 的 status 不入库，enabled 仅为它的派生值。
                if (object.has("bodyType")) { object.remove(Arrays.asList("status", "enabled")); }
                if (object.path("version").isNumber()) { object.remove("version"); }
            }
            List<String> fields = new ArrayList<>();
            object.fieldNames().forEachRemaining(fields::add);
            // 实体字段稳定排序；内嵌 JSON 保留来源顺序，便于查看顺序变化。
            if (entity) { Collections.sort(fields); }
            for (String field : fields) {
                JsonNode child = object.get(field);
                if (redact && SECRET_FIELDS.contains(field) && (child.isValueNode() || "x-default-auth".equals(field))) {
                    object.put(field, "***");
                } else if (redact && "url".equals(field) && child.isTextual()) {
                    object.put(field, safeSource(child.asText()));
                } else if (entity && ("schemaContent".equals(field) || "envContent".equals(field) || "examples".equals(field)) && child.isTextual()
                        && JsonUtils.isJson(child.asText())) {
                    JsonNode parsed = JsonUtils.fromJson(child.asText(), JsonNode.class);
                    if (parsed != null) {
                        JsonNode unredacted = rawJson && redact ? parsed.deepCopy() : null;
                        clean(parsed, false, redact, rawJson);
                        if (!rawJson) {
                            object.set(field, parsed);
                        } else if (redact && !Objects.equals(unredacted, parsed)) {
                            object.put(field, parsed.toString());
                        }
                    }
                } else {
                    clean(child, entity && Set.of("parametersSchema", "securityRequirements", "requestsSchemas", "responsesSchemas").contains(field), redact, rawJson);
                }
                // 请求/响应集合按模型标识比较；Schema 中的数组（示例、元组等）不排序。
                if (entity && Set.of("requestsSchemas", "responsesSchemas").contains(field) && child.isArray()) {
                    List<JsonNode> schemas = new ArrayList<>();
                    child.forEach(schemas::add);
                    schemas.sort(Comparator.comparing(schema -> schema.path("schemaName").asText() + "|"
                            + schema.path("contentType").asText() + "|" + schema.path("bodyType").asText()));
                    ((com.fasterxml.jackson.databind.node.ArrayNode) child).removeAll().addAll(schemas);
                }
                // 按选定顺序输出字段；JsonNode.equals 本身忽略对象键顺序。
                JsonNode cleaned = object.remove(field);
                object.set(field, cleaned);
            }
        } else if (node.isArray()) {
            node.forEach(child -> clean(child, entity, redact, rawJson));
        }
    }

    public static String safeSource(String source) {
        if (source == null) { return ""; }
        String safe = source.replaceFirst("(://)[^/@]+@", "$1***@");
        int queryStart = safe.indexOf('?');
        if (queryStart < 0) { return safe; }
        String base = safe.substring(0, queryStart);
        String[] parameters = safe.substring(queryStart + 1).split("&", -1);
        Set<String> secretKeys = Set.of("token", "accesstoken", "apikey", "key", "password", "secret",
                "signature", "authorization", "auth", "credential", "xamzsignature", "xamzcredential", "xamzsecuritytoken");
        for (int i = 0; i < parameters.length; i++) {
            int equals = parameters[i].indexOf('=');
            if (equals < 0) { continue; }
            try {
                String name = URLDecoder.decode(parameters[i].substring(0, equals), StandardCharsets.UTF_8.name())
                        .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (secretKeys.contains(name)) { parameters[i] = parameters[i].substring(0, equals + 1) + "***"; }
            } catch (Exception e) {
                return base;
            }
        }
        return base + "?" + String.join("&", parameters);
    }

    @Data
    public static class Change {
        private String type;
        private String name;
        private String path;
        private String method;
        private List<String> changedFields = new ArrayList<>();
        private String status;
        private boolean formatOnly;
        private String original;
        private String modified;
    }
}
