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

    private static final int MAX_ITERATIONS = 6;
    private static final Pattern DOC_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(doc://(\\d+)\\)");
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^#{1,4}\\s+(\\d+\\.|[一二三四五六七八九十]+[、.]|[\\u4e00-\\u9fa5]|Overview|Parameters|Response|Request).*");

    private static final String SYSTEM_PROMPT =
            "你是一个专业、严谨的 API 架构师与接口文档助理。你的任务是根据当前系统中的真实文档，解答开发者提出的业务调用与接口集成问题。\n\n" +
            "【可用工具】\n" +
            "1. search_docs: 根据关键词或路径搜索相关接口。用户提问时，请先思考同义词或对应英文（如：'退钱' -> '退款 refund cancel'）后再搜索。\n" +
            "2. get_doc: 阅读指定接口的详细参数、入参模型与说明。当需要梳理调用步骤或说明必填项时使用。\n\n" +
            "【行为规范与红线】\n" +
            "1. 真实性第一（禁止臆造）：你推荐的接口必须真实来源于工具调用结果。如果未检索到匹配接口，必须明确告知用户“当前项目中未找到相关接口”，严禁自行编造不存在的 URL 或参数。\n" +
            "2. 引用规范：正文中提及某个具体接口时，必须且只能使用规范链接格式：`[HTTP_METHOD URL](doc://{docId})`，例如：`[POST /api/v1/refund](doc://102)`。如果找不到该接口的真实 ID，请不要加 doc:// 链接。\n" +
            "3. 快速收敛与响应效率：\n" +
            "   - 一旦通过 search_docs 或 get_doc 获取到能解答用户核心问题的接口或说明文档，请立即总结输出方案，严禁继续发散搜索次要次级关键词；\n" +
            "   - 若连续搜索未找到结果，不要连续发起多次同义词盲搜；直接根据已有信息作答或告知用户未找到对应接口；\n" +
            "   - 尽量在 1~2 轮工具调用内完成问答，减少无谓等待。\n" +
            "4. 结构化表达与 Markdown 排版规范（至关重要）：\n" +
            "   - 结构清晰层次分明：使用标准 Markdown 分级标题（### 1. 接口概述、### 2. 请求说明、### 3. 返回关键字段等）组织内容；\n" +
            "   - 代码块必须严格闭合：HTTP 请求头如使用 ```http 代码块展示，必须紧接着在末尾用 ``` 闭合，切勿将后续的正文说明、章节标题或参数列表包裹在未闭合的代码块内；请求体与响应体示例必须使用独立的 ```json ... ``` 闭合代码块；\n" +
            "   - 参数说明规范：使用列表（如 `- **fieldName** (类型, 必填/可选): 说明`）或 Markdown 表格清晰展示，标注出核心必填入参和返回值中的关键串联字段；\n" +
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
            AiChatProvider provider = getChatProvider(config.getProvider());

            // 3. 初始化上下文与权限
            ApiUserVo user = SecurityUtils.getLoginUser();
            AgentContext context = AgentContext.builder()
                    .user(user)
                    .defaultProjectId(project.getId())
                    .verifiedDocIds(new LinkedHashSet<>())
                    .inspectedDocIds(new LinkedHashSet<>())
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

            if (StringUtils.isBlank(finalContent)) {
                finalContent = "未能检索到与当前问题直接匹配的接口或业务文档。建议您检查当前项目是否包含该模块，或使用高级搜索尝试不同的关键词。";
            }

            // 6. Grounding 校验与关联接口卡片组装，并自动修复 Markdown 代码块格式
            String cleanedContent = sanitizeMarkdownContent(processGroundingAndCleanLinks(finalContent, context));
            List<ApiDocSearchResultVo> relatedDocs = buildRelatedDocs(cleanedContent, context);

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
            String label = matcher.group(1);
            int docId = Integer.parseInt(matcher.group(2));
            if (isDocIdValidAndAccessible(docId, context)) {
                context.getVerifiedDocIds().add(docId);
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

        // 2. 仅当正文未显式格式化任何 doc:// 链接时（防模型输出偏差），才回退使用已深度查阅的接口（最多 4 个）
        if (targetDocIds.isEmpty() && context.getInspectedDocIds() != null) {
            for (Integer id : context.getInspectedDocIds()) {
                if (targetDocIds.size() >= 4) {
                    break;
                }
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
     * 自动修复未闭合的代码块并阻断代码块吞噬 Markdown 标题等结构元素
     */
    protected String sanitizeMarkdownContent(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        String[] lines = content.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inCodeBlock = false;
        String currentLang = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                currentLang = inCodeBlock ? trimmed.substring(3).trim().toLowerCase() : "";
                sb.append(line);
            } else {
                if (inCodeBlock) {
                    boolean isLikelyHeading = false;
                    if (trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ") || trimmed.startsWith("#### ")) {
                        // 在 http/json/xml 等典型 API 报文代码块中，任何 # 标题均不可能是合法代码，100% 为模型漏闭合导致的吞噬
                        if (currentLang.contains("http") || currentLang.contains("json") || currentLang.contains("xml") || currentLang.isEmpty()) {
                            isLikelyHeading = true;
                        } else if (MARKDOWN_HEADING_PATTERN.matcher(trimmed).matches()) {
                            isLikelyHeading = true;
                        }
                    }
                    if (isLikelyHeading) {
                        while (sb.length() > 0 && (sb.charAt(sb.length() - 1) == '\n' || sb.charAt(sb.length() - 1) == '\r')) {
                            sb.deleteCharAt(sb.length() - 1);
                        }
                        sb.append("\n```\n\n");
                        inCodeBlock = false;
                        currentLang = "";
                    }
                }
                sb.append(line);
            }
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        // 如果末尾仍处于未闭合状态，自动补齐闭合标记
        if (inCodeBlock) {
            sb.append("\n```");
        }
        return sb.toString();
    }
}
