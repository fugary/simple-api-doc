package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.exports.md.MdViewContext;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ApiDocHistoryQueryVo;
import com.fugary.simple.api.web.vo.query.ApiDocQueryVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
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
    private ApiProjectInfoDetailService apiDocSchemaService;

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Autowired
    private ApiDocViewGenerator apiDocViewGenerator;

    @GetMapping
    public SimpleResult<List<ApiDoc>> searchDoc(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiDoc> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        QueryWrapper<ApiDoc> queryWrapper = Wrappers.<ApiDoc>query()
                .eq("project_id", queryVo.getProjectId())
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY)
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
            result.add("historyCount", apiDocService.count(Wrappers.<ApiDoc>query().eq(ApiDocConstants.DB_MODIFY_FROM_KEY, id)));
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
            ApiProjectInfo projectInfo = apiProjectService.findOrCreateProjectInfo(apiDoc);
            if (projectInfo != null) {
                apiDoc.setInfoId(projectInfo.getId());
            }
        }
        ApiDocParseUtils.calcNewDocKey(apiDoc, folder);
        if (apiDoc.getVersion() == null) {
            apiDoc.setVersion(1);
        }
        apiDocService.saveApiDoc(SimpleModelUtils.addAuditInfo(apiDoc), null);
        return SimpleResultUtils.createSimpleResult(apiDoc);
    }

    @PostMapping("/updateApiDoc")
    public SimpleResult<ApiDoc> updateApiDoc(@RequestBody ApiDoc apiDoc) {
        if (!validateUserProject(apiDoc)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        ApiDoc existsDoc = apiDocService.getById(apiDoc.getId());
        if (apiDoc.getVersion() == null) {
            apiDoc.setVersion(1);
        }
        if (existsDoc != null && existsDoc.getVersion() != null) {
            apiDoc.setVersion(existsDoc.getVersion() + 1);
        }
        apiDocService.saveByApiDoc(existsDoc);
        apiDocService.update(Wrappers.<ApiDoc>update().eq("id", apiDoc.getId())
                .set(ApiDocConstants.STATUS_KEY, apiDoc.getStatus())
                .set(ApiDocConstants.MODIFIER_KEY, SecurityUtils.getLoginUserName())
                .set("locked", apiDoc.getLocked())
                .set("doc_version", apiDoc.getVersion())
                .set("modify_date", new Date()));
        return SimpleResultUtils.createSimpleResult(apiDoc);
    }

    @GetMapping("/loadDoc/{docId}")
    public SimpleResult<ApiDocDetailVo> loadDoc(@PathVariable("docId") Integer docId,
                                                @RequestParam(value = "md",
                                                        required = false,
                                                        defaultValue = "false") Boolean markdown) {
        ApiDoc apiDoc = apiDocService.getById(docId);
        ApiDocDetailVo apiDocVo = apiDocSchemaService.loadDetailVo(apiDoc);
        ApiProjectInfo apiInfo = apiProjectInfoService.getById(apiDocVo.getInfoId());
        if (apiInfo == null || !apiDocVo.getProjectId().equals(apiInfo.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfoDetailVo apiInfoDetailVo = apiProjectInfoDetailService.parseInfoDetailVo(apiInfo, apiDocVo);
        ApiProject apiProject = apiProjectService.getById(apiDocVo.getProjectId());
        apiInfoDetailVo.setProjectCode(apiProject.getProjectCode());
        apiDocVo.setProject(apiProject);
        apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
        SimpleResult<ApiDocDetailVo> result = SimpleResultUtils.createSimpleResult(apiDocVo);
        result.add("historyCount", apiDocService.count(Wrappers.<ApiDoc>query().eq(ApiDocConstants.DB_MODIFY_FROM_KEY, docId)));
        if (Boolean.TRUE.equals(markdown)) {
            String apiMarkdown = apiDocViewGenerator.generate(new MdViewContext(apiDocVo));
            apiDocVo.setApiMarkdown(apiMarkdown);
        }
        return result;
    }

    /**
     * 获取历史版本
     *
     * @param queryVo
     * @return
     */
    @PostMapping("/historyList")
    public SimpleResult<List<ApiDoc>> loadHistoryList(@RequestBody ApiDocQueryVo queryVo) {
        Integer docId = queryVo.getDocId();
        Page<ApiDoc> page = SimpleResultUtils.toPage(queryVo);
        ApiDoc currentDoc = apiDocService.getById(docId);
        if (currentDoc == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiDocService.page(page, Wrappers.<ApiDoc>query()
                        .eq(ApiDocConstants.DB_MODIFY_FROM_KEY, docId)
                        .orderByDesc("doc_version")))
                .add("current", currentDoc);
    }

    /**
     * 获取历史版本
     *
     * @param queryVo
     * @return
     */
    @PostMapping("/loadHistoryDiff")
    public SimpleResult<Map<String, ApiDoc>> loadHistoryDiff(@RequestBody ApiDocHistoryQueryVo queryVo) {
        Integer docId = queryVo.getDocId();
        Integer maxVersion = queryVo.getVersion();
        Page<ApiDoc> page = new Page<>(1, 2);
        apiDocService.page(page, Wrappers.<ApiDoc>query().eq(ApiDocConstants.DB_MODIFY_FROM_KEY, docId)
                .le(maxVersion != null, "doc_version", maxVersion)
                .orderByDesc("doc_version"));
        if (page.getRecords().isEmpty()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        } else {
            Map<String, ApiDoc> map = new HashMap<>(2);
            List<ApiDoc> docs = page.getRecords();
            map.put("modifiedDoc", docs.get(0));
            if (docs.size() > 1) {
                map.put("originalDoc", docs.get(1));
            }
            return SimpleResultUtils.createSimpleResult(map);
        }
    }

    @PostMapping("/recoverFromHistory")
    public SimpleResult<ApiDoc> recoverFromHistory(@RequestBody ApiDocHistoryQueryVo historyVo) {
        ApiDoc history = apiDocService.getById(historyVo.getDocId()); // 加载历史
        ApiDoc target = null;
        if (history != null && history.getModifyFrom() != null) {
            target = apiDocService.getById(history.getModifyFrom());
        }
        if (history == null || target == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiDoc apiDoc = apiDocService.copyFromHistory(history, target);
        apiDocService.saveApiDoc(apiDoc, target); // 更新
        return SimpleResultUtils.createSimpleResult(apiDoc);
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
        ApiFolder folder = apiFolderService.getById(apiDoc.getFolderId());
        return apiDocService.copyApiDoc(apiDoc, folder);
    }

    protected boolean validateUserProject(ApiDoc apiDoc) {
        if (apiDoc != null) {
            return apiProjectService.validateUserProject(apiDoc.getProjectId());
        }
        return true;
    }

}
