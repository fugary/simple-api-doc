package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ApiDocHistoryQueryVo;
import com.fugary.simple.api.web.vo.query.ApiDocQueryVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/docs")
public class ApiDocController {

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiFolderService apiFolderService;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Autowired
    private ApiDocHistoryService apiDocHistoryService;

    @GetMapping
    public SimpleResult<List<ApiDoc>> searchDoc(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiDoc> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        QueryWrapper<ApiDoc> queryWrapper = Wrappers.<ApiDoc>query()
                .eq("project_id", queryVo.getProjectId())
                .and(StringUtils.isNotBlank(keyword),
                        query -> query.like(StringUtils.isNotBlank(keyword), "doc_name", keyword)
                                .or().like(StringUtils.isNotBlank(keyword), "url", keyword)
                                .or().like(StringUtils.isNotBlank(keyword), "operationId", keyword)
                                .or().like(StringUtils.isNotBlank(keyword), "summary", keyword));
        return SimpleResultUtils.createSimpleResult(apiDocService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiDoc> getDoc(@PathVariable("id") Integer id) {
        ApiDoc apiDoc = apiDocService.getById(id);
        if (apiDoc == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        SimpleResult<ApiDoc> result = SimpleResultUtils.createSimpleResult(apiDoc);
        if (ApiDocConstants.DOC_TYPE_MD.equals(apiDoc.getDocType())) {
            result.add("historyCount", apiDocHistoryService.count(Wrappers.<ApiDocHistory>query().eq("doc_id", id)));
        }
        return result;
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> removeDoc(@PathVariable("id") Integer id) {
        ApiDoc ApiDoc = apiDocService.getById(id);
        if (!validateUserProject(ApiDoc)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiDocService.deleteDoc(id));
    }

    @PostMapping
    public SimpleResult<ApiDoc> saveDoc(@RequestBody ApiDoc apiDoc) {
        ApiFolder folder = apiFolderService.getById(apiDoc.getFolderId());
        if (folder == null || !folder.getProjectId().equals(apiDoc.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!validateUserProject(apiDoc)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (apiDocService.existsApiDoc(apiDoc)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        if (apiDoc.getInfoId() == null) {
            ApiProjectInfo projectInfo = apiProjectInfoService.loadByProjectId(apiDoc.getProjectId(), apiDoc.getFolderId());
            if (projectInfo != null) {
                apiDoc.setInfoId(projectInfo.getId());
            }
        }
        if (StringUtils.isBlank(apiDoc.getDocKey())) {
            apiDoc.setDocKey(SimpleModelUtils.uuid());
        }
        if (apiDoc.getDocVersion() == null) {
            apiDoc.setDocVersion(1);
        }
        apiDocService.saveApiDoc(SimpleModelUtils.addAuditInfo(apiDoc), null);
        return SimpleResultUtils.createSimpleResult(apiDoc);
    }

    @GetMapping("/loadDoc/{docId}")
    public SimpleResult<ApiDocDetailVo> loadDoc(@PathVariable("docId") Integer docId) {
        ApiDoc apiDoc = apiDocService.getById(docId);
        ApiDocDetailVo apiDocVo = apiDocSchemaService.loadDetailVo(apiDoc);
        ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDocVo.getInfoId());
        if (apiInfo == null || !apiDocVo.getProjectId().equals(apiInfo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfoDetailVo apiInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(apiInfo, apiDocVo);
        ApiProject apiProject = apiProjectService.getById(apiDocVo.getProjectId());
        apiInfoDetailVo.setProjectCode(apiProject.getProjectCode());
        apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
        return SimpleResultUtils.createSimpleResult(apiDocVo);
    }

    /**
     * 获取历史版本
     *
     * @param queryVo
     * @return
     */
    @PostMapping("/historyList")
    public SimpleResult<List<ApiDocHistory>> loadHistoryList(@RequestBody ApiDocQueryVo queryVo) {
        Integer docId = queryVo.getDocId();
        Page<ApiDocHistory> page = SimpleResultUtils.toPage(queryVo);
        return SimpleResultUtils.createSimpleResult(apiDocHistoryService.page(page, Wrappers.<ApiDocHistory>query().eq("doc_id", docId)
                .orderByDesc("create_date")));
    }

    /**
     * 获取历史版本
     *
     * @param queryVo
     * @return
     */
    @PostMapping("/loadHistoryDiff")
    public SimpleResult<Map<String, ApiDocHistory>> loadHistoryDiff(@RequestBody ApiDocHistoryQueryVo queryVo) {
        Integer docId = queryVo.getDocId();
        Integer maxVersion = queryVo.getDocVersion();
        Page<ApiDocHistory> page = new Page<>(1, 2);
        apiDocHistoryService.page(page, Wrappers.<ApiDocHistory>query().eq("doc_id", docId)
                .le(maxVersion != null, "doc_version", maxVersion)
                .orderByDesc("doc_version"));
        if (page.getRecords().isEmpty()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        } else {
            Map<String, ApiDocHistory> map = new HashMap<>(2);
            List<ApiDocHistory> docs = page.getRecords();
            map.put("modifiedDoc", docs.get(0));
            if (docs.size() > 1) {
                map.put("originalDoc", docs.get(1));
            }
            return SimpleResultUtils.createSimpleResult(map);
        }
    }

    @PostMapping("/copyApiDoc/{docId}")
    public SimpleResult<ApiDoc> copyApiDoc(@PathVariable("docId") Integer docId) {
        ApiDoc apiDoc = apiDocService.getById(docId);
        if (apiDoc == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!validateUserProject(apiDoc)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiDocService.copyApiDoc(apiDoc));
    }

    protected boolean validateUserProject(ApiDoc apiDoc) {
        if (apiDoc != null) {
            return apiProjectService.validateUserProject(apiDoc.getProjectId());
        }
        return true;
    }

}
