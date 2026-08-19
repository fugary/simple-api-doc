package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.AiCache;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.mapper.api.AiCacheMapper;
import com.fugary.simple.api.mapper.api.ApiProjectShareMapper;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.AiService;
import com.fugary.simple.api.service.ai.provider.AiChatProvider;
import com.fugary.simple.api.service.ai.provider.AiChatRequest;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.utils.servlet.HttpRequestUtils;
import com.fugary.simple.api.web.vo.AiGenericTaskReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import com.fugary.simple.api.service.ai.provider.AbstractAiChatProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private static final Pattern EXCEPTION_PREFIX_PATTERN = Pattern.compile("^([a-zA-Z0-9_.$]+(Exception|Error):\\s*)+");

    @Autowired
    private AiConfigProperties aiConfigProperties;

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private AiCacheMapper aiCacheMapper;

    @Autowired
    private ApiProjectShareMapper apiProjectShareMapper;

    @Autowired
    @Qualifier("taskScheduler")
    private Executor taskExecutor;

    @Autowired
    private List<AiChatProvider> chatProviders;

    private AiChatProvider getChatProvider(String providerCode) {
        String code = StringUtils.isBlank(providerCode) ? "OPENAI" : providerCode;
        return chatProviders.stream()
                .filter(p -> code.equalsIgnoreCase(p.getProviderCode()))
                .findFirst()
                .orElseGet(() -> chatProviders.stream()
                        .filter(p -> "OPENAI".equalsIgnoreCase(p.getProviderCode()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No suitable AI provider found")));
    }

    private AiConfig resolveAiConfig(Integer configId) {
        if (configId != null) {
            AiConfig config = aiConfigService.getById(configId);
            if (config == null) {
                throw new SimpleRuntimeException(SystemErrorConstants.CODE_2012);
            }
            if (!Integer.valueOf(1).equals(config.getStatus())) {
                throw new SimpleRuntimeException(SystemErrorConstants.CODE_2013);
            }
            return config;
        }
        AiConfig defaultConfig = aiConfigService.getDefaultAiConfig();
        if (defaultConfig == null || !Integer.valueOf(1).equals(defaultConfig.getStatus())) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2014);
        }
        return defaultConfig;
    }

    @Override
    public String executeGenericTask(AiGenericTaskReq req) {
        AiConfig config = resolveAiConfig(req.getConfigId());
        if (req != null && StringUtils.isNotBlank(req.getModel())) {
            config = SimpleModelUtils.copy(config, AiConfig.class);
            config.setDefaultModel(req.getModel());
        }
        final AiConfig targetAiConfig = config;
        if (StringUtils.isBlank(targetAiConfig.getApiKey())) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2014);
        }
        String systemPrompt = req.getSystemPrompt();
        String userMessageContent = req.getUserMessage();
        String cacheType = req.getCacheType();
        String projectId = req.getProjectId();
        String docId = req.getDocId();

        String promptHash = DigestUtils.md5Hex(systemPrompt);
        String rawKey = targetAiConfig.getDefaultModel() + ":" + promptHash + ":" + userMessageContent;
        String cacheKey = DigestUtils.sha256Hex(rawKey);

        boolean cacheExists = false;
        try {
            AiCache cache = aiCacheMapper.selectById(cacheKey);
            if (cache != null) {
                cacheExists = true;
                if (cache.getStatus() != null && cache.getStatus() == 1 && StringUtils.isNotBlank(cache.getCacheValue())) {
                    log.info("AI {} 命中缓存, key: {}", cacheType, cacheKey);
                    return cache.getCacheValue();
                } else if (cache.getStatus() != null && cache.getStatus() == 0) {
                    throw new SimpleRuntimeException(SystemErrorConstants.CODE_2017);
                }
            }
        } catch (SimpleRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取 AI 缓存失败，将降级直接调用 AI", e);
        }
        try {
            Long pendingCount = aiCacheMapper.selectCount(Wrappers.<AiCache>lambdaQuery().eq(AiCache::getStatus, 0));
            if (pendingCount != null && pendingCount >= aiConfigProperties.getMaxPendingTasks()) {
                throw new SimpleRuntimeException(SystemErrorConstants.CODE_2016);
            }
            initAiCache(cacheKey, systemPrompt, userMessageContent, projectId, docId, cacheType, targetAiConfig, cacheExists);
        } catch (SimpleRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("写入 AI 缓存状态失败", e);
        }
        long startTime = System.currentTimeMillis();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            AiChatProvider provider = getChatProvider(targetAiConfig.getProvider());
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setSystemPrompt(systemPrompt);
            chatRequest.setUserMessage(userMessageContent);
            chatRequest.setTemperature(0.3); // Default temperature as per original logic

            AiChatResponse chatResponse = provider.chat(targetAiConfig, chatRequest);
            String generatedSample = chatResponse.getContent();

            if (StringUtils.isNotBlank(generatedSample)) {
                updateCacheOnSuccess(cacheKey, chatResponse, System.currentTimeMillis() - startTime);
                log.info("AI {} 成功，写入缓存, key: {}", cacheType, cacheKey);
            }
            return generatedSample;
        }, taskExecutor).whenComplete((res, ex) -> {
            if (ex != null) {
                updateCacheOnFailure(cacheKey, ex, System.currentTimeMillis() - startTime);
            }
        });

        try {
            return future.get(1, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2017);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(getSimpleErrorMessage(cause), cause);
        }
    }

    @Override
    public String generateSample(AiGenericTaskReq req) {
        String systemPrompt = "你是一个专门用于生成模拟数据的接口开发助手。请根据用户提供的 OpenAPI/JSON Schema 结构生成合理的示例 JSON 数据。规则：\n" +
                "1. 必须只返回合法的纯 JSON 数据。\n" +
                "2. 不要包含任何多余的解释文字、代码块标记（如 ```json）。\n" +
                "3. 根据 Schema 中的 type、description、example 或 format，生成逼真的模拟数据。\n" +
                "4. 必须全面覆盖所有定义的属性（包括所有的嵌套对象和数组，以及 $ref 引用的组件），不能随意遗漏字段或简化数据结构，数组建议生成1-2条数据。\n" +
                "5. 返回的结果必须是根据 `schema` 结构定义生成的实例数据对象。";

        AiGenericTaskReq genericReq = SimpleModelUtils.copy(req, AiGenericTaskReq.class);
        if (genericReq == null) {
            genericReq = new AiGenericTaskReq();
        }
        genericReq.setSystemPrompt(systemPrompt);
        genericReq.setUserMessage(StringUtils.defaultIfBlank(req.getSchemaContent(), req.getUserMessage()));
        genericReq.setCacheType("mock_data");
        return executeGenericTask(genericReq);
    }

    @Override
    public String generateDescriptions(AiGenericTaskReq req) {
        String schemaContent = req != null ? req.getSchemaContent() : null;
        String lang = req != null ? req.getLang() : null;
        String extraPrompt = req != null ? StringUtils.defaultIfBlank(req.getPrompt(), req.getUserMessage()) : null;
        String mode = req != null && StringUtils.isNotBlank(req.getMode()) ? req.getMode() : "missing";
        String languageDesc = "zh-CN".equalsIgnoreCase(lang) ? "中文" : "英文";
        boolean withExample = req == null || req.getWithExample() == null || req.getWithExample();

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

        AiGenericTaskReq genericReq = SimpleModelUtils.copy(req, AiGenericTaskReq.class);
        if (genericReq == null) {
            genericReq = new AiGenericTaskReq();
        }
        genericReq.setSystemPrompt(systemPrompt.toString());
        genericReq.setUserMessage(userMessage.toString());
        genericReq.setCacheType("generate_desc");
        return executeGenericTask(genericReq);
    }

    @Override
    public String generateModel(AiGenericTaskReq req) {
        String prompt = req != null ? StringUtils.defaultIfBlank(req.getPrompt(), req.getUserMessage()) : null;
        String lang = req != null ? req.getLang() : null;
        boolean withExample = req == null || req.getWithExample() == null || req.getWithExample();
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
            systemPrompt.append("5. 示例提取（example）：严禁胡乱臆测或凭空捏造无依据的虚假示例值！仅当用户提示词/表格中明确提供了具体示例数据（如 Sample JSON、具体字段示例值、表格示例列）时，才提取填入 example；若用户需求描述中未提供明确的示例数据，切勿随意捏造示例，避免虚假示例产生误导。\n");
        } else {
            systemPrompt.append("5. 示例（example）：本次生成无需生成任何 example 字段，严禁在 Schema 中添加 example 属性。\n");
        }
        systemPrompt.append("6. 复合类型识别：若字段类型为自定义对象或列表（如 `StlInfo`、`Array<ErrorInfo>`），合理推断为 object 或 array 结构。\n")
                .append("7. 字段命名合理，类型推断准确（如 integer, string, boolean, array, object 等）。");

        AiGenericTaskReq genericReq = SimpleModelUtils.copy(req, AiGenericTaskReq.class);
        if (genericReq == null) {
            genericReq = new AiGenericTaskReq();
        }
        genericReq.setSystemPrompt(systemPrompt.toString());
        genericReq.setUserMessage(prompt);
        genericReq.setCacheType("generate_model");
        return executeGenericTask(genericReq);
    }

    private void initAiCache(String cacheKey, String systemPrompt, String userMessageContent,
                             String projectId, String docId, String cacheType,
                             AiConfig currentAiConfig, boolean cacheExists) {
        AiCache aiCache = new AiCache();
        aiCache.setCacheKey(cacheKey);
        aiCache.setStatus(0);
        aiCache.setCacheValue("");
        aiCache.setModelName(currentAiConfig.getDefaultModel());
        aiCache.setProvider(currentAiConfig.getProvider());
        aiCache.setBaseUrl(currentAiConfig.getBaseUrl());
        aiCache.setCreatedAt(new Date());
        aiCache.setPrompt(systemPrompt + "\n" + userMessageContent);
        aiCache.setProjectId(projectId);
        aiCache.setDocId(docId);
        String userName = SecurityUtils.getLoginUserName();
        if (StringUtils.isBlank(userName)) {
            String shareId = SecurityUtils.getLoginShareId();
            if (StringUtils.isNotBlank(shareId)) {
                ApiProjectShare share = apiProjectShareMapper.selectOne(
                        Wrappers.<ApiProjectShare>lambdaQuery().eq(ApiProjectShare::getShareId, shareId));
                userName = (share != null && StringUtils.isNotBlank(share.getCreator())) ? share.getCreator() : shareId;
            }
        }
        aiCache.setUserName(userName);
        aiCache.setClientIp(HttpRequestUtils.getClientIp());
        aiCache.setCacheType(cacheType);
        if (cacheExists) {
            aiCacheMapper.updateById(aiCache);
        } else {
            aiCacheMapper.insert(aiCache);
        }
    }

    private void updateCacheOnSuccess(String cacheKey, AiChatResponse chatResponse, long costTime) {
        try {
            aiCacheMapper.update(null, Wrappers.<AiCache>lambdaUpdate()
                    .set(AiCache::getCacheValue, chatResponse.getContent())
                    .set(AiCache::getStatus, 1)
                    .set(costTime > 0, AiCache::getCostTime, costTime)
                    .set(AiCache::getRawResponse, chatResponse.getRawResponse())
                    .set(AiCache::getUpdatedAt, new Date())
                    .set(chatResponse.getPromptTokens() != null, AiCache::getPromptTokens, chatResponse.getPromptTokens())
                    .set(chatResponse.getCompletionTokens() != null, AiCache::getCompletionTokens, chatResponse.getCompletionTokens())
                    .set(chatResponse.getTotalTokens() != null, AiCache::getTotalTokens, chatResponse.getTotalTokens())
                    .eq(AiCache::getCacheKey, cacheKey));
        } catch (Exception cacheEx) {
            log.error("写入 AI 缓存失败", cacheEx);
        }
    }

    private void updateCacheOnFailure(String cacheKey, Throwable ex, long costTime) {
        try {
            String errorMessage = getSimpleErrorMessage(ex);
            if (errorMessage != null && errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 1000);
            }
            aiCacheMapper.update(null, Wrappers.<AiCache>lambdaUpdate()
                    .set(AiCache::getStatus, 2)
                    .set(costTime > 0, AiCache::getCostTime, costTime)
                    .set(AiCache::getErrorMessage, errorMessage)
                    .set(AiCache::getUpdatedAt, new Date())
                    .eq(AiCache::getCacheKey, cacheKey));
        } catch (Exception ignore) {}
    }

    @Override
    public AiChatResponse testAiConfig(Integer configId, AiGenericTaskReq req) {
        AiConfig aiConfig = resolveAiConfig(configId);
        return testAiConfig(aiConfig, req);
    }

    @Override
    public AiChatResponse testAiConfig(AiConfig config, AiGenericTaskReq req) {
        if (config == null) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2012);
        }
        AiConfig targetConfig = config;
        if (req != null && StringUtils.isNotBlank(req.getModel())) {
            targetConfig = SimpleModelUtils.copy(targetConfig, AiConfig.class);
            targetConfig.setDefaultModel(req.getModel());
        }
        if (StringUtils.isBlank(targetConfig.getBaseUrl()) || StringUtils.isBlank(targetConfig.getApiKey())
                || StringUtils.isBlank(targetConfig.getDefaultModel())) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2014);
        }

        String prompt = req != null ? StringUtils.defaultIfBlank(req.getUserMessage(), req.getPrompt()) : null;
        AiChatProvider provider = getChatProvider(targetConfig.getProvider());
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setSystemPrompt("你是一个有用的 AI 助手。");
        chatRequest.setUserMessage(prompt);
        chatRequest.setTemperature(0.5);

        String cacheKey = UUID.randomUUID().toString();
        try {
            initAiCache(cacheKey, chatRequest.getSystemPrompt(), prompt, null, null, "test_config", targetConfig, false);
        } catch (Exception e) {
            log.error("写入 AI 缓存状态失败", e);
        }

        try {
            long startTime = System.currentTimeMillis();
            AiChatResponse chatResponse = provider.chat(targetConfig, chatRequest);
            long endTime = System.currentTimeMillis();
            chatResponse.setElapsedTime(endTime - startTime);

            if (StringUtils.isNotBlank(chatResponse.getContent())) {
                updateCacheOnSuccess(cacheKey, chatResponse, chatResponse.getElapsedTime());
                log.info("AI {} 成功，写入缓存, key: {}", "test_config", cacheKey);
            }
            return chatResponse;
        } catch (Exception e) {
            log.error("AI 测试连接失败", e);
            updateCacheOnFailure(cacheKey, e, 0L);
            throw new SimpleRuntimeException(500, "测试连接失败: " + getSimpleErrorMessage(e));
        }
    }

    public static String getSimpleErrorMessage(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        
        Throwable current = e;
        String fallbackJsonMsg = null;
        // 1. 遍历异常链查找 RestClientResponseException 或提取嵌套的 JSON 错误
        while (current != null) {
            if (current instanceof RestClientResponseException) {
                return AbstractAiChatProvider.extractErrorMessage((RestClientResponseException) current);
            }
            if (fallbackJsonMsg == null && StringUtils.isNotBlank(current.getMessage())) {
                String jsonMsg = AbstractAiChatProvider.extractJsonErrorMessage(current.getMessage());
                if (StringUtils.isNotBlank(jsonMsg)) {
                    fallbackJsonMsg = jsonMsg;
                }
            }
            current = current.getCause();
        }
        
        if (fallbackJsonMsg != null) {
            return fallbackJsonMsg;
        }

        // 2. 剥离无意义的包装异常（如 ExecutionException），寻找顶层有意义的错误信息
        Throwable target = e;
        while (target.getCause() != null && target.getCause() != target) {
            if (StringUtils.isNotBlank(target.getMessage())
                    && !(target instanceof java.util.concurrent.ExecutionException)
                    && !(target instanceof java.util.concurrent.CompletionException)) {
                break;
            }
            target = target.getCause();
        }
        
        String message = target.getMessage();
        if (StringUtils.isBlank(message)) {
            return target.getClass().getSimpleName();
        }
        
        message = EXCEPTION_PREFIX_PATTERN.matcher(message.trim()).replaceFirst("").trim();
        if (StringUtils.isBlank(message)) {
            return target.getClass().getSimpleName();
        }
        return StringUtils.abbreviate(message, 500);
    }

    @Override
    public List<String> loadModels(Integer configId) {
        AiConfig config = resolveAiConfig(configId);
        return loadModels(config);
    }

    @Override
    public List<String> loadModels(AiConfig config) {
        if (config == null) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2012);
        }
        if (StringUtils.isBlank(config.getBaseUrl()) || StringUtils.isBlank(config.getApiKey())) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2014);
        }
        AiChatProvider provider = getChatProvider(config.getProvider());
        return provider.loadModels(config);
    }

    @Override
    public boolean isEnabled() {
        if (!aiConfigProperties.isEnabled()) {
            return false;
        }
        AiConfig config = aiConfigService.getDefaultAiConfig();
        return config != null && StringUtils.isNotBlank(config.getApiKey());
    }
}
