package com.fugary.simple.api.service.impl.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.ai.agent.AiAgentService;
import com.fugary.simple.api.service.ai.agent.tool.AgentContext;
import com.fugary.simple.api.service.ai.agent.tool.AgentTool;
import com.fugary.simple.api.service.ai.agent.tool.AiToolCall;
import com.fugary.simple.api.service.ai.agent.tool.AiToolDefinition;
import com.fugary.simple.api.service.ai.provider.AiChatProvider;
import com.fugary.simple.api.service.ai.provider.AiChatResponse;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.ai.AiAgentChatReqVo;
import com.fugary.simple.api.web.vo.doc.ApiDocSearchResultVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 接口文档 Agent 服务实现
 */
@Slf4j
@Service
public class AiAgentServiceImpl implements AiAgentService {

    private static final int MAX_ITERATIONS = 8;
    private static final Pattern DOC_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(doc://(\\d+)\\)");
    private static final Pattern MARKDOWN_FENCE_PATTERN = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    private static final String SYSTEM_PROMPT =
            "你是一个专业、严谨的 API 架构师与接口文档助理。你的任务是根据当前系统中的真实文档，解答开发者提出的业务调用与接口集成问题。\n\n" +
            "【可用工具】\n" +
            "1. search_docs: 根据关键词或路径按字面包含匹配文档，不会自动扩展同义词或删改尾缀；match=any 表示任意词匹配，match=all 表示每个词必须命中；结果按相关度排序，返回 total、page、hasMore 与 docs。\n" +
            "2. get_doc: 阅读指定接口的详细参数、入参模型与说明。当需要梳理调用步骤或说明必填项时使用。\n\n" +
            "【行为规范与核心指引】\n" +
            "1. 真实性第一：推荐的接口、URL 与参数必须来源于工具结果；检索无匹配时说明本次未找到，不得推断整个项目不存在该能力，也不得编造接口。\n" +
            "2. 引用规范：具体接口使用 `[HTTP_METHOD URL](doc://{docId})`，说明文档使用 `[文档名称](doc://{docId})`；ID 必须来自工具结果。\n" +
            "3. 根据用户问题与已有证据决定检索策略：\n" +
            "   - 优先使用用户给出的明确名称或路径，保留路径原文；自然语言问题自行提炼有区分度的核心词。\n" +
            "   - 根据上下文判断同义词、英文名称或拼写变体，避免将不同含义混为一谈；必要时用少量同义表达扩大召回，多个独立条件用 match=all 保留约束，同义表达用 match=any；不要将独立条件与同义表达混在同一次查询中。\n" +
            "   - 结果不足时，hasMore=true 可用 page 翻页，或依据已有信息调整查询、查阅相关详情；证据已足够回答时直接作答，避免无依据地反复扩展和重复查询。\n" +
            "   - 回答参数、请求/响应字段或调用步骤之前必须用 get_doc 查阅对应文档，不可从摘要猜测细节。候选命中不等于相关或完整，按问题筛选并说明证据不足或结果范围，不能把有限候选当作全量清单。\n" +
            "4. 结构化表达与 Markdown 排版规范：\n" +
            "   - 结构清晰层次分明：使用标准 Markdown 分级标题（### 1. 接口概述、### 2. 请求说明、### 3. 返回关键字段等）组织内容；\n" +
            "   - 代码块必须严格闭合：HTTP 请求头如使用 ```http 代码块展示，必须紧接着在末尾用 ``` 闭合，切勿将后续的正文说明、章节标题或参数列表包裹在未闭合的代码块内；请求体与响应体示例必须使用独立的 ```json ... ``` 闭合代码块；\n" +
            "   - 参数说明规范：使用列表（如 `- **fieldName** (类型, 必填/可选): 说明`）或 Markdown 表格清晰展示核心字段；\n" +
            "   - 语言简练，直奔主题，避免客套。";

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private List<AiChatProvider> chatProviders;

    @Autowired
    private List<AgentTool> agentTools;

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiFolderService apiFolderService;

    @Autowired
    private ApiProjectAccessService apiProjectAccessService;

    @Override
    public void streamChat(AiAgentChatReqVo req, SseEmitter emitter) {
        AtomicBoolean clientDisconnected = new AtomicBoolean(false);
        emitter.onCompletion(() -> clientDisconnected.set(true));
        emitter.onTimeout(() -> clientDisconnected.set(true));
        emitter.onError(e -> clientDisconnected.set(true));

        try {
            if (!sendSseEvent(emitter, "status", Map.of("status", "thinking", "message", "正在分析业务意图与检索策略..."))) {
                return;
            }

            // 1. 校验项目存在且有权限（Controller 层已做前置参数校验，此处做业务层权限断言）
            ApiProject project = apiProjectService.getById(req.getProjectId());
            if (project == null || !apiProjectAccessService.canAccessProject(project, ApiGroupAuthority.READABLE)) {
                throw new RuntimeException("无权访问该项目文档或项目不存在");
            }

            // 2. 解析 AI 配置与 Provider
            AiConfig config = resolveAiConfig(req.getConfigId());
            if (StringUtils.isNotBlank(req.getModel())) {
                config = SimpleModelUtils.copy(config, AiConfig.class);
                config.setDefaultModel(req.getModel().trim());
            }
            AiChatProvider provider = getChatProvider(config.getProvider());

            // 3. 初始化上下文与权限
            ApiUserVo user = SecurityUtils.getLoginUser();
            AgentContext context = AgentContext.builder()
                    .user(user)
                    .defaultProjectId(project.getId())
                    .build();

            // 4. 准备工具与历史会话
            Map<String, AgentTool> toolMap = agentTools.stream()
                    .collect(Collectors.toMap(AgentTool::getName, Function.identity()));
            List<AiToolDefinition> toolDefinitions = agentTools.stream()
                    .map(AgentTool::toDefinition)
                    .collect(Collectors.toList());

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            String userPrompt = "[当前关注项目: " + project.getProjectName() + " (ID: " + project.getId() + ")]\n" + req.getQuery();
            messages.add(Map.of("role", "user", "content", userPrompt));

            // 5. ReAct 循环调度
            String finalContent = "";
            int round = 0;
            Set<String> calledToolFingerprints = new HashSet<>();

            while (round++ < MAX_ITERATIONS) {
                if (clientDisconnected.get()) {
                    log.info("Client disconnected, stopping agent loop early");
                    return;
                }
                boolean isLastRound = round == MAX_ITERATIONS;
                AiChatResponse response = provider.chatWithTools(config, messages, toolDefinitions, isLastRound ? "none" : "auto");
                if (clientDisconnected.get()) {
                    return;
                }
                List<AiToolCall> toolCalls = response.getToolCalls();

                if (toolCalls == null || toolCalls.isEmpty() || isLastRound) {
                    finalContent = response.getContent() != null ? response.getContent() : "";
                    break;
                }

                // 记录模型的 tool_calls 消息（完整保留原生 message 包含 thought_signature / reasoning_content 等关键元数据）
                if (response.getAssistantMessage() != null && !response.getAssistantMessage().isEmpty()) {
                    messages.add(new HashMap<>(response.getAssistantMessage()));
                } else {
                    Map<String, Object> assistantMsg = new HashMap<>();
                    assistantMsg.put("role", "assistant");
                    if (StringUtils.isNotBlank(response.getContent())) {
                        assistantMsg.put("content", response.getContent());
                    }
                    List<Map<String, Object>> rawToolCalls = new ArrayList<>();
                    for (AiToolCall tc : toolCalls) {
                        rawToolCalls.add(Map.of(
                                "id", ensureToolCallId(tc),
                                "type", "function",
                                "function", Map.of("name", tc.getName(), "arguments", tc.getArguments() != null ? tc.getArguments() : "{}")
                        ));
                    }
                    assistantMsg.put("tool_calls", rawToolCalls);
                    messages.add(assistantMsg);
                }

                // 顺序执行各个工具调用并推流
                for (AiToolCall toolCall : toolCalls) {
                    if (clientDisconnected.get()) {
                        break;
                    }
                    String toolName = toolCall.getName();
                    if (toolName != null && toolName.contains(":")) {
                        toolName = toolName.substring(toolName.lastIndexOf(':') + 1);
                    }
                    String fp = toolName + ":" + toolCall.getArguments();
                    if (!calledToolFingerprints.add(fp)) {
                        Map<String, Object> repeatMsg = new HashMap<>();
                        repeatMsg.put("role", "tool");
                        repeatMsg.put("tool_call_id", ensureToolCallId(toolCall));
                        repeatMsg.put("content", "该查询已执行过，请参考此前返回结果，不要重复查询相同内容。");
                        messages.add(repeatMsg);
                        continue;
                    }

                    if (!sendSseEvent(emitter, "tool_start", Map.of(
                            "tool", toolName != null ? toolName : "",
                            "arguments", toolCall.getArguments() != null ? toolCall.getArguments() : "{}"
                    ))) {
                        clientDisconnected.set(true);
                        break;
                    }

                    AgentTool tool = toolMap.get(toolName);
                    String toolResult = tool != null ? tool.execute(toolCall.getArguments(), context) : "未知工具: " + toolCall.getName();

                    if (!sendSseEvent(emitter, "tool_end", Map.of(
                            "tool", toolName != null ? toolName : "",
                            "summary", StringUtils.abbreviate(toolResult.replaceAll("\\s+", " "), 100)
                    ))) {
                        clientDisconnected.set(true);
                        break;
                    }

                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", ensureToolCallId(toolCall));
                    toolMsg.put("content", toolResult);
                    messages.add(toolMsg);
                }

                if (clientDisconnected.get()) {
                    log.info("Client disconnected during tool execution, terminating agent loop early");
                    return;
                }
            }

            if (clientDisconnected.get()) {
                return;
            }

            boolean hasDocs = !context.getDiscoveredDocIds().isEmpty() || !context.getInspectedDocIds().isEmpty();

            if (StringUtils.isBlank(finalContent) && hasDocs) {
                try {
                    AiChatResponse summaryRes = provider.chatWithTools(config, messages, toolDefinitions, "none");
                    if (summaryRes != null && StringUtils.isNotBlank(summaryRes.getContent())) {
                        finalContent = summaryRes.getContent();
                    }
                } catch (Exception e) {
                    log.warn("生成最终总结失败", e);
                }
            }

            if (clientDisconnected.get()) return;

            // 6. Grounding 校验与关联接口卡片组装，并自动修复 Markdown 代码块格式
            String cleanedContent = sanitizeMarkdownContent(processGroundingAndCleanLinks(finalContent, context));
            List<ApiDocSearchResultVo> relatedDocs = buildRelatedDocs(cleanedContent, context);
            if (StringUtils.isBlank(cleanedContent)) {
                cleanedContent = buildFallbackContent(relatedDocs);
            }

            // 7. 推送最终结果事件
            if (!sendSseEvent(emitter, "related_docs", relatedDocs)) return;
            if (!sendSseEvent(emitter, "delta", Map.of("text", cleanedContent))) return;
            sendSseEvent(emitter, "finish", Map.of("status", "SUCCESS"));
            try {
                emitter.complete();
            } catch (Exception ignore) {}
        } catch (Exception e) {
            log.error("AiAgentServiceImpl streamChat error", e);
            if (!clientDisconnected.get()) {
                sendSseEvent(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "AI 服务响应异常"));
                try {
                    emitter.complete();
                } catch (Exception ignore) {}
            }
        }
    }

    private String buildFallbackContent(List<ApiDocSearchResultVo> docs) {
        if (docs.isEmpty()) {
            return "未能检索到与当前问题直接匹配的接口或业务文档。建议您检查当前项目是否包含该模块，或使用高级搜索尝试不同的关键词。";
        }
        StringBuilder content = new StringBuilder("AI 暂未生成完整回答，以下是本次检索或查阅到的候选文档，可点击查看：\n\n");
        for (ApiDocSearchResultVo doc : docs) {
            String label = ApiDocConstants.DOC_TYPE_API.equals(doc.getDocType())
                    ? StringUtils.defaultString(doc.getMethod()) + " " + StringUtils.defaultString(doc.getUrl())
                    : StringUtils.defaultString(doc.getDocName());
            // 标签按普通文本处理，避免文档名称或 URL 中的 Markdown 符号破坏链接。
            label = label.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
                    .replaceAll("\\s+", " ").trim();
            content.append("- [").append(label).append("](doc://").append(doc.getId()).append(")\n");
        }
        return content.toString();
    }

    private boolean sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(JsonUtils.toJson(data)));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Failed to send SSE event: {}, client might have disconnected or completed: {}", eventName, e.getMessage());
            return false;
        }
    }

    private String ensureToolCallId(AiToolCall toolCall) {
        return toolCall.getId() != null ? toolCall.getId() : UUID.randomUUID().toString();
    }

    /**
     * 校验正文中出现的 doc://{id}，剔除未经验证或无权限的虚假 ID
     */
    private String processGroundingAndCleanLinks(String content, AgentContext context) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        Matcher matcher = DOC_LINK_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int docId = Integer.parseInt(matcher.group(2));
            if (isDocIdValidAndAccessible(docId, context)) {
                matcher.appendReplacement(sb, "[$1](doc://$2)");
            } else {
                // 剔除无效链接，降级为普通文本标签
                matcher.appendReplacement(sb, "$1");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isDocIdValidAndAccessible(int docId, AgentContext context) {
        ApiDoc doc = apiDocService.getById(docId);
        if (doc == null || doc.getModifyFrom() != null) {
            return false;
        }
        if (context.getDefaultProjectId() != null && !Objects.equals(doc.getProjectId(), context.getDefaultProjectId())) {
            return false;
        }
        return apiProjectAccessService.canAccessDoc(doc, ApiGroupAuthority.READABLE);
    }

    private List<ApiDocSearchResultVo> buildRelatedDocs(String content, AgentContext context) {
        Set<Integer> targetDocIds = new LinkedHashSet<>();
        // 1. 优先收纳正文中通过 Grounding 校验引用的真实有效文档（严格对应回答方案所涉接口）
        Matcher matcher = DOC_LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            targetDocIds.add(Integer.parseInt(matcher.group(2)));
        }

        // 2. 仅当正文未显式格式化任何 doc:// 链接时（防模型输出偏差），才回退使用已查阅或已检索的接口（最多 6 个）
        if (targetDocIds.isEmpty()) {
            Set<Integer> candidates = new LinkedHashSet<>(context.getInspectedDocIds());
            candidates.addAll(context.getDiscoveredDocIds());
            for (Integer id : candidates) {
                if (targetDocIds.size() >= 6) break;
                if (isDocIdValidAndAccessible(id, context)) {
                    targetDocIds.add(id);
                }
            }
        }

        if (targetDocIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ApiDoc> docList = apiDocService.list(Wrappers.<ApiDoc>query()
                .in("id", targetDocIds)
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY));
        if (docList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Integer> projectIds = docList.stream().map(ApiDoc::getProjectId).collect(Collectors.toSet());
        Map<Integer, ApiProject> projects = apiProjectService.list(Wrappers.<ApiProject>query()
                        .select("id", "project_code", "project_name").in("id", projectIds)).stream()
                .collect(Collectors.toMap(ApiProject::getId, Function.identity()));
        Map<Integer, String> folderPaths = apiFolderService.calcFolderNameMap(apiFolderService.list(Wrappers.<ApiFolder>query()
                .select("id", "parent_id", "folder_name", "folder_code").in("project_id", projectIds)));

        Map<Integer, ApiDocSearchResultVo> resultMap = new HashMap<>();
        for (ApiDoc doc : docList) {
            ApiDocSearchResultVo vo = SimpleModelUtils.copy(doc, ApiDocSearchResultVo.class);
            ApiProject project = projects.get(doc.getProjectId());
            if (project != null) {
                vo.setProjectCode(project.getProjectCode());
                vo.setProjectName(project.getProjectName());
            }
            vo.setFolderPath(folderPaths.getOrDefault(doc.getFolderId(), ""));
            resultMap.put(doc.getId(), vo);
        }

        // 按原有顺序排列
        List<ApiDocSearchResultVo> resultList = new ArrayList<>();
        for (Integer id : targetDocIds) {
            ApiDocSearchResultVo vo = resultMap.get(id);
            if (vo != null) {
                resultList.add(vo);
            }
        }
        return resultList;
    }

    private AiConfig resolveAiConfig(Integer configId) {
        if (configId != null && configId > 0) {
            AiConfig config = aiConfigService.getById(configId);
            if (config != null && Integer.valueOf(1).equals(config.getStatus())) {
                return config;
            }
        }
        AiConfig defaultConfig = aiConfigService.getDefaultAiConfig();
        if (defaultConfig != null && Integer.valueOf(1).equals(defaultConfig.getStatus())) {
            return defaultConfig;
        }
        throw new RuntimeException("当前无可用的 AI 配置，请在系统设置中启用 AI 配置");
    }

    private AiChatProvider getChatProvider(String providerCode) {
        String code = StringUtils.isBlank(providerCode) ? "OPENAI" : providerCode;
        return chatProviders.stream()
                .filter(p -> code.equalsIgnoreCase(p.getProviderCode()))
                .findFirst()
                .orElseGet(() -> chatProviders.stream()
                        .filter(p -> "OPENAI".equalsIgnoreCase(p.getProviderCode()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("未找到支持的 AI Provider: " + code)));
    }

    /**
     * 仅在末尾补齐未闭合代码围栏，不根据正文含义猜测代码块的结束位置。
     */
    protected String sanitizeMarkdownContent(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        String openFence = null;
        for (String line : content.split("\r?\n", -1)) {
            Matcher matcher = MARKDOWN_FENCE_PATTERN.matcher(line);
            if (!matcher.matches()) continue;
            String fence = matcher.group(1);
            String suffix = matcher.group(2);
            if (openFence == null) {
                if (fence.charAt(0) != '`' || !suffix.contains("`")) {
                    openFence = fence;
                }
            } else if (fence.charAt(0) == openFence.charAt(0)
                    && fence.length() >= openFence.length() && suffix.trim().isEmpty()) {
                openFence = null;
            }
        }
        return openFence == null ? content : content + "\n" + openFence;
    }
}
