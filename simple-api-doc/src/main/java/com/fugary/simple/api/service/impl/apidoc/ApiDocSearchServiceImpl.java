package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.doc.ApiDocSearchResultVo;
import com.fugary.simple.api.web.vo.query.ApiDocSearchQueryVo;
import com.fugary.simple.api.web.vo.query.SimplePage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApiDocSearchServiceImpl implements ApiDocSearchService {
    @Autowired
    private ApiDocService apiDocService;
    @Autowired
    private ApiProjectService apiProjectService;
    @Autowired
    private ApiFolderService apiFolderService;
    @Autowired
    private ApiProjectAccessService apiProjectAccessService;

    @Override
    public Page<ApiDocSearchResultVo> search(ApiDocSearchQueryVo query, ApiProjectShare share) {
        QueryWrapper<ApiDoc> wrapper = Wrappers.<ApiDoc>query()
                .select("id", "project_id", "folder_id", "doc_name", "doc_type", "url", "method", "status")
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY)
                .in("doc_type", ApiDocConstants.DOC_TYPE_API, ApiDocConstants.DOC_TYPE_MD)
                .eq(query.getDocType() != null, "doc_type", query.getDocType())
                .eq(query.getMethod() != null, "method", query.getMethod());
        contains(wrapper, "doc_name", query.getDocName());
        contains(wrapper, "url", query.getUrl());
        if (StringUtils.isNotBlank(query.getUrl()) || query.getMethod() != null) {
            wrapper.eq("doc_type", ApiDocConstants.DOC_TYPE_API);
        }
        String keyword = StringUtils.trimToEmpty(query.getKeyword());
        if (!keyword.isEmpty()) {
            wrapper.and(w -> {
                contains(w, "doc_name", keyword);
                w.or(u -> contains(u, "url", keyword));
                w.or(c -> c.nested(md -> {
                    md.eq("doc_type", ApiDocConstants.DOC_TYPE_MD);
                    contains(md, "doc_content", keyword);
                }).or(api -> {
                    api.eq("doc_type", ApiDocConstants.DOC_TYPE_API);
                    contains(api, "description", keyword);
                }));
            });
        }
        String content = StringUtils.trimToEmpty(query.getContent());
        if (!content.isEmpty()) {
            wrapper.and(body -> body.nested(md -> {
                md.eq("doc_type", ApiDocConstants.DOC_TYPE_MD);
                contains(md, "doc_content", content);
            }).or(api -> {
                api.eq("doc_type", ApiDocConstants.DOC_TYPE_API);
                contains(api, "description", content);
            }));
        }
        if (share != null) {
            Set<Integer> docIds = SimpleModelUtils.getShareDocIds(share.getShareDocs());
            wrapper.eq("project_id", share.getProjectId())
                    .exists("select 1 from t_api_project p where p.id = t_api_doc.project_id and p.status = {0}",
                            ApiDocConstants.STATUS_ENABLED)
                    .eq(ApiDocConstants.STATUS_KEY, ApiDocConstants.STATUS_ENABLED)
                    .in(!docIds.isEmpty(), "id", docIds);
        } else {
            wrapper.eq(query.getProjectId() != null, "project_id", query.getProjectId())
                    .eq(query.getStatus() != null, ApiDocConstants.STATUS_KEY, query.getStatus());
            readableProjects(wrapper, "t_api_doc", "project_id");
        }
        wrapper.orderByDesc("modify_date", "id");
        Page<ApiDoc> docs = apiDocService.page(page(query), wrapper);
        Page<ApiDocSearchResultVo> result = new Page<>(docs.getCurrent(), docs.getSize(), docs.getTotal());
        if (docs.getRecords().isEmpty()) {
            return result;
        }
        Set<Integer> projectIds = docs.getRecords().stream().map(ApiDoc::getProjectId).collect(Collectors.toSet());
        Map<Integer, ApiProject> projects = apiProjectService.list(Wrappers.<ApiProject>query()
                        .select("id", "project_code", "project_name").in("id", projectIds)).stream()
                .collect(Collectors.toMap(ApiProject::getId, Function.identity()));
        Map<Integer, String> folderPaths = apiFolderService.calcFolderNameMap(apiFolderService.list(Wrappers.<ApiFolder>query()
                .select("id", "parent_id", "folder_name", "folder_code").in("project_id", projectIds)));
        // 正文仅为当前结果页加载，且仅返回有限长度的摘要。
        String snippetKeyword = !content.isEmpty() ? content : keyword;
        Map<Integer, ApiDoc> bodies = snippetKeyword.isEmpty() ? Collections.emptyMap()
                : apiDocService.list(Wrappers.<ApiDoc>query().select("id",
                        "CASE WHEN doc_type = 'md' THEN doc_content ELSE description END AS description")
                        .in("id", docs.getRecords().stream().map(ApiDoc::getId).collect(Collectors.toList())))
                .stream().collect(Collectors.toMap(ApiDoc::getId, Function.identity()));
        result.setRecords(docs.getRecords().stream().map(doc -> {
            ApiDocSearchResultVo item = SimpleModelUtils.copy(doc, ApiDocSearchResultVo.class);
            ApiProject project = projects.get(doc.getProjectId());
            if (project != null) {
                item.setProjectCode(project.getProjectCode());
                item.setProjectName(project.getProjectName());
            }
            item.setFolderPath(folderPaths.getOrDefault(doc.getFolderId(), ""));
            ApiDoc body = bodies.get(doc.getId());
            if (body != null) {
                item.setSnippet(snippet(body.getDescription(), snippetKeyword));
            }
            return item;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public Page<ApiProject> searchProjects(ApiDocSearchQueryVo query) {
        QueryWrapper<ApiProject> wrapper = Wrappers.<ApiProject>query().select("id", "project_code", "project_name");
        contains(wrapper, "project_name", query.getDocName());
        readableProjects(wrapper, "t_api_project", "id");
        return apiProjectService.page(page(query), wrapper.orderByDesc("id"));
    }

    private <T> void readableProjects(QueryWrapper<T> wrapper, String table, String projectColumn) {
        if (SecurityUtils.getLoginUser() == null) {
            wrapper.eq("id", -1);
        } else if (!SecurityUtils.isAdmin()) {
            apiProjectAccessService.addProjectRelatedGroupCodeQuery(wrapper, table, projectColumn,
                    null, SecurityUtils.getLoginUserName());
        }
    }

    private <T> Page<T> page(ApiDocSearchQueryVo query) {
        SimplePage input = query.getPage();
        return new Page<>(input == null ? 1 : Math.max(1, input.getPageNumber()),
                input == null ? 20 : Math.max(1, Math.min(50, input.getPageSize())));
    }

    private <T> void contains(QueryWrapper<T> wrapper, String column, String value) {
        if (StringUtils.isNotBlank(value)) {
            // 参数绑定且按字面包含匹配，百分号和下划线不作为 SQL 通配符。
            wrapper.apply("LOCATE({0}, LOWER(" + column + ")) > 0", value.trim().toLowerCase(Locale.ROOT));
        }
    }

    static String snippet(String text, String keyword) {
        if (StringUtils.isEmpty(text)) return "";
        int match = StringUtils.indexOfIgnoreCase(text, keyword);
        int start = Math.max(0, match - 60);
        int end = Math.min(text.length(), Math.max(start + 240, match + keyword.length()));
        return (start > 0 ? "…" : "") + text.substring(start, end).replaceAll("\\s+", " ")
                + (end < text.length() ? "…" : "");
    }
}
