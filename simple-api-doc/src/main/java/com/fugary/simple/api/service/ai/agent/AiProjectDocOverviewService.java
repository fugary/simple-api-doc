package com.fugary.simple.api.service.ai.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fugary.simple.api.config.AiConfigProperties;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.utils.JsonUtils;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/** 生成当前可读项目的完整轻量目录，不导出 Schema、环境变量或完整正文。 */
@Service
public class AiProjectDocOverviewService {
    @Autowired
    private ApiDocService apiDocService;
    @Autowired
    private ApiFolderService apiFolderService;
    @Autowired
    private ApiProjectAccessService apiProjectAccessService;
    @Autowired
    private AiConfigProperties aiConfigProperties;

    public Overview build(ApiProject project) {
        if (project == null || project.getId() == null
                || !apiProjectAccessService.canAccessProject(project, ApiGroupAuthority.READABLE)) {
            throw new IllegalArgumentException("无权访问该项目文档或项目不存在");
        }
        // 正文摘要在数据库端限长，避免读取全部 Markdown 和接口 Schema。
        List<ApiDoc> docs = apiDocService.list(Wrappers.<ApiDoc>query()
                .select("id", "folder_id", "doc_name", "doc_type", "method", "url", "status",
                        "LEFT(CASE WHEN doc_type = 'md' THEN doc_content ELSE description END, 500) AS description")
                .eq("project_id", project.getId())
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY)
                .in("doc_type", ApiDocConstants.DOC_TYPE_API, ApiDocConstants.DOC_TYPE_MD)
                .orderByAsc("folder_id", "sort_id", "id"));
        Map<Integer, String> folders = apiFolderService.calcFolderNameMap(apiFolderService.list(
                Wrappers.<ApiFolder>query().select("id", "parent_id", "folder_name", "folder_code")
                        .eq("project_id", project.getId())));
        int limit = Math.max(0, aiConfigProperties.getProjectOverviewMaxChars());
        String content = format(project, docs, folders, true, limit);
        boolean withDescriptions = !content.isEmpty();
        if (!withDescriptions) {
            content = format(project, docs, folders, false, limit);
        }
        if (content.isEmpty()) {
            return new Overview("", 0,
                    "项目文档概览超出长度限制，本次未附加，改用按需搜索。");
        }
        return new Overview(content, docs.size(), "已附加当前项目全部 " + docs.size() + " 份文档的"
                + (withDescriptions ? "简明概览" : "完整目录（已省略摘要）")
                + "，正在分析问题；需要参数细节时会继续查阅详情。");
    }

    private String format(ApiProject project, List<ApiDoc> docs, Map<Integer, String> folders,
                          boolean withDescriptions, int limit) {
        StringBuilder content = new StringBuilder("【项目文档概览：目录完整】\n");
        content.append("项目：").append(JsonUtils.toJson(project.getProjectName()))
                .append("；文档数量：").append(docs.size()).append('\n');
        if (withDescriptions && StringUtils.isNotBlank(project.getDescription())) {
            content.append("项目简介：").append(JsonUtils.toJson(summary(project.getDescription(), 240))).append('\n');
        }
        content.append("包含当前版本的 API 和 Markdown 文档；status=1 启用，status=0 禁用；历史快照不包含。\n")
                .append("每行是 JSON 数组，字段顺序：[docId, 类型, 方法, 路径, 名称, status")
                .append(withDescriptions ? ", 简短摘要]\n" : "]；摘要因长度限制省略。\n");
        if (content.length() > limit) {
            return "";
        }
        Integer previousFolder = null;
        boolean first = true;
        for (ApiDoc doc : docs) {
            if (first || !Objects.equals(previousFolder, doc.getFolderId())) {
                content.append("目录：").append(JsonUtils.toJson(
                        StringUtils.defaultIfBlank(doc.getFolderId() == null ? null : folders.get(doc.getFolderId()), "根目录"))).append('\n');
                previousFolder = doc.getFolderId();
                first = false;
            }
            List<Object> row = new ArrayList<>(Arrays.asList(doc.getId(), doc.getDocType(),
                    doc.getMethod(), doc.getUrl(), doc.getDocName(), doc.getStatus()));
            if (withDescriptions) {
                row.add(summary(doc.getDescription(), 120));
            }
            content.append(JsonUtils.toJson(row)).append('\n');
            if (content.length() > limit) {
                return ""; // 超限即丢弃本轮结果，避免继续拼接或发送部分目录。
            }
        }
        return content.toString();
    }

    private String summary(String value, int maxLength) {
        return StringUtils.abbreviate(StringUtils.normalizeSpace(StringUtils.defaultString(value)), maxLength);
    }

    @Value
    public static class Overview {
        String content;
        int docCount;
        String message;
    }
}
