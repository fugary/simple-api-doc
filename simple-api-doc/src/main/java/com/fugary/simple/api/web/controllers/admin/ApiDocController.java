package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return SimpleResultUtils.createSimpleResult(apiDocService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult removeDoc(@PathVariable("id") Integer id) {
        ApiDoc ApiDoc = apiDocService.getById(id);
        if (!validateUserProject(ApiDoc)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiDocService.deleteDoc(id));
    }

    @PostMapping
    public SimpleResult saveDoc(@RequestBody ApiDoc apiDoc) {
        if (apiDoc.getFolderId() != null) {
            ApiFolder folder = apiFolderService.getById(apiDoc.getFolderId());
            if (folder == null || !folder.getProjectId().equals(apiDoc.getProjectId())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
            }
        }
        if (!validateUserProject(apiDoc)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiDocService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiDoc)));
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
        apiDocVo.setProjectInfoDetail(apiInfoDetailVo);
        return SimpleResultUtils.createSimpleResult(apiDocVo);
    }

    protected boolean validateUserProject(ApiDoc apiDoc) {
        if (apiDoc != null) {
            return apiProjectService.validateUserProject(apiDoc.getProjectId());
        }
        return true;
    }

}
