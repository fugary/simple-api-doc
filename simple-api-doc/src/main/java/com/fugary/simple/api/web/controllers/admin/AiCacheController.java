package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ai.AiCacheQueryVo;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * AI Cache 管理 Controller
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/admin/ai/caches")
public class AiCacheController {

    @Autowired
    private AiService aiService;

    @Autowired
    private AiCacheMapper aiCacheMapper;

    @Autowired
    private ApiProjectAccessService apiProjectAccessService;

    @GetMapping
    public SimpleResult<List<AiCache>> search(@ModelAttribute AiCacheQueryVo queryVo) {
        Page<AiCache> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        if (queryVo.getProjectId() != null && !apiProjectAccessService.canAccessProject(queryVo.getProjectId(), ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        QueryWrapper<AiCache> queryWrapper = Wrappers.<AiCache>query()
                .eq(queryVo.getProjectId() != null, "project_id", queryVo.getProjectId())
                .eq(SecurityUtils.isAdmin() && StringUtils.isNotBlank(queryVo.getUserName()), "user_name", StringUtils.trimToEmpty(queryVo.getUserName()))
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .eq(StringUtils.isNotBlank(queryVo.getModelName()), "model_name", StringUtils.trimToEmpty(queryVo.getModelName()))
                .ge(queryVo.getStartDate() != null, "created_at", queryVo.getStartDate())
                .le(queryVo.getEndDate() != null, "created_at", queryVo.getEndDate())
                .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like("cache_key", keyword)
                        .or().like("cache_value", keyword))
                .orderByDesc("created_at");
        if (!SecurityUtils.isAdmin() && queryVo.getProjectId() == null) {
            String userName = SecurityUtils.getLoginUserName();
            queryWrapper.and(wrapper -> {
                wrapper.eq("user_name", userName)
                       .or(inner -> apiProjectAccessService.addProjectRelatedGroupCodeQuery(inner, "t_ai_cache", "project_id", null, userName));
            });
        }
        return SimpleResultUtils.createSimpleResult(aiCacheMapper.selectPage(page, queryWrapper));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") String id) {
        if (!SecurityUtils.isAdmin()) {
            AiCache aiCache = aiCacheMapper.selectById(id);
            if (aiCache == null || !StringUtils.equals(aiCache.getUserName(), SecurityUtils.getLoginUserName())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
            }
        }
        return SimpleResultUtils.createSimpleResult(aiCacheMapper.deleteById(id) > 0);
    }

    private SimpleResult<String> executeTask(String systemPrompt, String userMessage, String cacheType, Map<String, Object> payload, String errorLogMsg) {
        String projectId = Objects.toString(payload.get("projectId"), null);
        Object configIdObj = payload.get("configId");
        Integer configId = configIdObj != null ? Integer.valueOf(configIdObj.toString()) : null;
        String model = Objects.toString(payload.get("model"), null);

        AiGenericTaskReq genericReq = new AiGenericTaskReq();
        genericReq.setSystemPrompt(systemPrompt);
        genericReq.setUserMessage(userMessage);
        genericReq.setCacheType(cacheType);
        genericReq.setProjectId(projectId);
        genericReq.setConfigId(configId);
        genericReq.setModel(model);
        try {
            String result = aiService.executeGenericTask(genericReq);
            return SimpleResultUtils.createSimpleResult(result);
        } catch (SimpleRuntimeException e) {
            if (StringUtils.isNotBlank(e.getMessage())) {
                return SimpleResultUtils.createError(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500, e.getMessage());
            }
            return SimpleResultUtils.createSimpleResult(e.getCode() != null ? e.getCode() : SystemErrorConstants.CODE_500);
        } catch (Exception e) {
            log.error(errorLogMsg, e);
            return SimpleResultUtils.createError(e.getMessage());
        }
    }

    @PostMapping("/generate-descriptions")
    public SimpleResult<String> generateDescriptions(@RequestBody Map<String, Object> payload) {
        String schemaContent = Objects.toString(payload.get("schemaContent"), null);
        String lang = (String) payload.get("lang");
        String extraPrompt = (String) payload.get("prompt");
        String mode = Objects.toString(payload.get("mode"), "missing");
        String languageDesc = "zh-CN".equalsIgnoreCase(lang) ? "中文" : "英文";
        boolean withExample = payload.get("withExample") == null || Boolean.parseBoolean(payload.get("withExample").toString());
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一个资深的 API 与 OpenAPI 设计专家。基于用户提供的现有 JSON Schema 结构与参考提示信息（如业务需求、文档、表格、示例数据等），智能补全与完善 JSON Schema。\n")
                .append("规则：\n")
                .append("1. 必须只返回合法的纯 JSON Schema 对象，严禁包含任何 markdown 格式标记（如 ```json）或额外的解释文字。\n")
                .append("2. 规范性：输出直接为 OpenAPI 3.0 / JSON Schema 规范的对象结构，顶层必须包含 type（如 \"object\" 或 \"array\"），对象类型必须包含 properties 结构。\n")
                .append("3. 严格保持已有顺序、结构与引用：严格保留已有属性字段的顺序、字段名、数据类型（type）以及模型引用（$ref），切勿打乱已有顺序或随意更改/删除已有属性的类型和 $ref 引用结构。\n")
                .append("4. 描述（description）：为字段生成准确的").append(languageDesc).append("业务描述。\n")
                .append("5. 枚举提取（enum）：若提示文本、表格列或字段说明中包含枚举候选值（如 `success/failed`、`1: 成功, 0: 失败`、`枚举值: A, B, C`、`可选值：...`、`true/false` 等），必须准确提取并填入该字段的 `enum` 数组（例如 `[\"success\", \"failed\"]` 或 `[1, 0]`）。\n");
        if (withExample) {
            systemPrompt.append("6. 示例（example）：若属性包含枚举 `enum`，可自动将首个枚举值设为 example；若提示词/表格中包含明确示例列或示例数据（如 Sample JSON、具体字段值），准确提取填入 example；若均未提供具体示例，保持 example 为空即可，严禁凭空编造无依据的虚假示例值。\n");
        } else {
            systemPrompt.append("6. 示例（example）：本次补全无需生成任何 example 字段，严禁在 Schema 中添加 example 属性。\n");
        }
        systemPrompt.append("7. 表格与多列文本识别：若用户粘贴的是表格或制表符/空格分隔的多列文本（如 字段名 类型 描述 示例），准确对应各列并补全相应属性名、类型映射、描述、枚举及示例值。\n")
                .append("8. 增量扩充：如果参考提示信息中包含已有 Schema 中未定义的新字段或示例数据，可在相应 properties 中合理追加新属性并给出对应 type, description 等。\n");
        if ("all".equalsIgnoreCase(mode)) {
            systemPrompt.append("9. 生成模式为【全量重新补全】：对所有字段重新生成完善的").append(languageDesc).append("描述及枚举").append(withExample ? "（及提示词中提供的示例值）" : "").append("，并严格保留现有字段顺序、类型与 $ref 结构。");
        } else {
            systemPrompt.append("9. 生成模式为【补全缺失信息】：保留已有的 description").append(withExample ? " 和 example" : "").append("，主要针对缺失 description").append(withExample ? "、example" : "").append("、enum 的字段以及参考提示中的新字段进行补全。");
        }
        StringBuilder userMessage = new StringBuilder();
        if (StringUtils.isNotBlank(extraPrompt)) {
            userMessage.append("【参考文档/示例数据/附加提示词】：\n").append(extraPrompt.trim()).append("\n\n");
        }
        userMessage.append("【当前 JSON Schema】：\n").append(StringUtils.isNotBlank(schemaContent) ? schemaContent : "{}");
        return executeTask(systemPrompt.toString(), userMessage.toString(), "generate_desc", payload, "智能补全 Schema 失败");
    }

    @PostMapping("/generate-model")
    public SimpleResult<String> generateModel(@RequestBody Map<String, Object> payload) {
        String prompt = Objects.toString(payload.get("prompt"), null);
        String lang = (String) payload.get("lang");
        boolean withExample = payload.get("withExample") == null || Boolean.parseBoolean(payload.get("withExample").toString());
        String languageDesc = "zh-CN".equalsIgnoreCase(lang) ? "中文" : "英文";
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一个资深的 API 与 OpenAPI 设计专家。基于用户提供的业务需求描述、表格文本或文档，生成一个完整且规范的数据模型（JSON Schema）对象。\n")
                .append("规则：\n")
                .append("1. 必须只返回合法的纯 JSON 对象，不要包含任何 markdown 格式标记（如 ```json）或额外的解释文字。\n")
                .append("2. 返回的 JSON 对象包含以下 3 个根属性：\n")
                .append("   - \"schemaName\": 模型的英文名称，推荐采用 PascalCase 大驼峰命名规范（如 UserInfoVo, CreateOrderReq）。\n")
                .append("   - \"description\": 模型的业务与功能描述（使用").append(languageDesc).append("）。\n")
                .append("   - \"schema\": 符合 OpenAPI 3.0 / JSON Schema 规范的对象结构，必须包含 type (\"object\"), properties (属性列表), optional required (必填字段数组)，并且每个属性节点需指定 type, description (").append(languageDesc).append("描述), format (可选格式), enum (可选枚举数组) 等。\n")
                .append("3. 表格与多列文本识别：若用户粘贴的是表格或制表符/空格分隔的多列文本（如 字段名 类型 描述 示例），必须准确对应解析出字段名、类型、描述、枚举值与示例值。\n")
                .append("4. 枚举提取（enum）：若提示文本或字段描述中包含枚举取值范围（如 `success/failed`、`1:成功, 0:失败`、`枚举值: A, B, C`、`可选值：...` 等），必须准确提取为 `enum` 数组（如 `[\"success\", \"failed\"]`）。\n");
        if (withExample) {
            systemPrompt.append("5. 示例提取（example）：严禁胡乱臆测或凭空捏造无依据的虚假示例值！仅当用户提示词/表格中明确提供了具体示例数据（如 Sample JSON、具体字段示例值、表格示例列）时，才提取填入 example；若用户需求描述中未提供明确的示例数据，切勿随意捏造示例，保持 example 为空即可，避免虚假示例产生误导。\n");
        } else {
            systemPrompt.append("5. 示例（example）：本次生成无需生成任何 example 字段，严禁在 Schema 中添加 example 属性。\n");
        }
        systemPrompt.append("6. 复合类型识别：若字段类型为自定义对象或列表（如 `StlInfo`、`Array<ErrorInfo>`），合理推断为 object 或 array 结构。\n")
                .append("7. 字段命名合理，类型推断准确（如 integer, string, boolean, array, object 等）。");
        return executeTask(systemPrompt.toString(), prompt, "generate_model", payload, "生成模型失败");
    }
}
