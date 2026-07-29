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
            String groupCodesStr = apiProjectAccessService.loadReadableGroupCodesSql(userName);
            queryWrapper.and(wrapper -> wrapper.eq("user_name", userName)
                    .or().exists("select 1 from t_api_project p where p.id = t_ai_cache.project_id and p.user_name={0} and (p.group_code is null or p.group_code = '')", userName)
                    .or(StringUtils.isNotBlank(groupCodesStr),
                            item -> item.exists("select 1 from t_api_project p where p.id = t_ai_cache.project_id and p.group_code in ('" + groupCodesStr + "')")));
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

        AiGenericTaskReq genericReq = new AiGenericTaskReq();
        genericReq.setSystemPrompt(systemPrompt);
        genericReq.setUserMessage(userMessage);
        genericReq.setCacheType(cacheType);
        genericReq.setProjectId(projectId);
        genericReq.setConfigId(configId);
        try {
            String result = aiService.executeGenericTask(genericReq);
            return SimpleResultUtils.createSimpleResult(result);
        } catch (SimpleRuntimeException e) {
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
        String languageDesc = "zh-CN".equalsIgnoreCase(lang) ? "中文" : "英文";
        String systemPrompt = "你是一个资深的 API 文档专家。基于提供的 JSON Schema 结构及参考提示信息，推断字段的业务含义，生成准确的" + languageDesc + "描述。\n" +
                "规则：\n" +
                "1. 严格返回 JSON 对象格式。\n" +
                "2. Key 为 JSON Schema 中的层级路径（如 properties.username），Value 为生成的描述。\n" +
                "3. 不要包含任何多余的解释文字或 markdown 格式，只返回纯 JSON。";
        String userMessage = StringUtils.isNotBlank(extraPrompt)
                ? "【参考文档/附加提示词】：\n" + extraPrompt.trim() + "\n\n【JSON Schema 结构】：\n" + schemaContent
                : schemaContent;
        return executeTask(systemPrompt, userMessage, "generate_desc", payload, "生成描述失败");
    }

    @PostMapping("/generate-model")
    public SimpleResult<String> generateModel(@RequestBody Map<String, Object> payload) {
        String prompt = Objects.toString(payload.get("prompt"), null);
        String lang = (String) payload.get("lang");
        String languageDesc = "zh-CN".equalsIgnoreCase(lang) ? "中文" : "英文";
        String systemPrompt = "你是一个资深的 API 与 OpenAPI 设计专家。基于用户提供的业务需求描述，生成一个完整且规范的数据模型（JSON Schema）对象。\n" +
                "规则：\n" +
                "1. 必须只返回合法的纯 JSON 对象，不要包含任何 markdown 格式标记（如 ```json）或额外的解释文字。\n" +
                "2. 返回的 JSON 对象包含以下 3 个根属性：\n" +
                "   - \"schemaName\": 模型的英文名称，推荐采用 PascalCase 大驼峰命名规范（如 UserInfoVo, CreateOrderReq）。\n" +
                "   - \"description\": 模型的业务与功能描述（使用" + languageDesc + "）。\n" +
                "   - \"schema\": 符合 OpenAPI 3.0 / JSON Schema 规范的对象结构，必须包含 type (\"object\"), properties (属性列表), optional required (必填字段数组)，并且每个属性节点需指定 type, description (" + languageDesc + "描述), example (可选示例值), format (可选格式) 等。\n" +
                "3. 字段命名合理，类型推断准确（如 integer, string, boolean, array, object 等）。";
        return executeTask(systemPrompt, prompt, "generate_model", payload, "生成模型失败");
    }
}
