package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.exports.ApiSchemaContentUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ApiDocHistoryQueryVo;
import com.fugary.simple.api.web.vo.query.ProjectComponentQueryVo;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fugary.simple.api.utils.security.SecurityUtils.getLoginUser;

/**
 * Create date 2025/7/8<br>
 *
 * @author gary.fu
 */
@Slf4j
@RestController
@RequestMapping("/admin/info/detail")
public class ApiProjectInfoDetailController {

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiGroupService apiGroupService;

    @GetMapping
    public SimpleResult<List<ApiProjectInfoDetail>> search(@ModelAttribute ProjectComponentQueryVo queryVo) {
        Page<ApiProjectInfoDetail> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        QueryWrapper<ApiProjectInfoDetail> queryWrapper = Wrappers.<ApiProjectInfoDetail>query()
                .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like("schema_name", keyword)
                        .or().like("description", keyword))
                .eq("project_id", queryVo.getProjectId())
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY)
                .eq(queryVo.getInfoId() != null, "info_id", queryVo.getInfoId())
                .and(queryVo.getLocked() != null, wrapper -> {
                    wrapper.eq("locked", queryVo.getLocked());
                    if (!queryVo.getLocked()) {
                        wrapper.or().isNull("locked");
                    }
                })
                .eq("body_type", queryVo.getBodyType());
        return SimpleResultUtils.createSimpleResult(apiProjectInfoDetailService.page(page, queryWrapper));
    }

    @PostMapping("/loadInfoDetails")
    public SimpleResult<List<ApiProjectInfoDetail>> loadInfoDetails(@RequestBody ProjectComponentQueryVo queryVo) {
        QueryWrapper<ApiProjectInfoDetail> queryWrapper = Wrappers.<ApiProjectInfoDetail>query()
                .eq("project_id", queryVo.getProjectId())
                .isNull(ApiDocConstants.DB_MODIFY_FROM_KEY)
                .eq(queryVo.getInfoId() != null, "info_id", queryVo.getInfoId())
                .eq("body_type", queryVo.getBodyType());
        return SimpleResultUtils.createSimpleResult(apiProjectInfoDetailService.list(queryWrapper));
    }

    @PostMapping("/loadInfoDetail")
    public SimpleResult<ApiProjectInfoDetail> loadInfoDetail(@RequestBody ProjectComponentQueryVo queryVo) {
        QueryWrapper<ApiProjectInfoDetail> queryWrapper = Wrappers.<ApiProjectInfoDetail>query()
                .eq("project_id", queryVo.getProjectId())
                .eq("info_id", queryVo.getInfoId())
                .eq("schema_name", queryVo.getSchemaName())
                .eq("body_type", queryVo.getBodyType());
        ApiProjectInfoDetail infoDetail = apiProjectInfoDetailService.getOne(queryWrapper);
        List<ApiProjectInfoDetail> infoDetails = apiProjectInfoDetailService.findRelatedInfoDetails(infoDetail);
        return SimpleResultUtils.createSimpleResult(infoDetail)
                .add("components", (Serializable) infoDetails);
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProjectInfoDetail> get(@PathVariable("id") Integer id) {
        ApiProjectInfoDetail infoDetail = apiProjectInfoDetailService.getById(id);
        if (infoDetail == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        List<ApiProjectInfoDetail> infoDetails = apiProjectInfoDetailService.findRelatedInfoDetails(infoDetail);
        long historyCount = apiProjectInfoDetailService.count(Wrappers.<ApiProjectInfoDetail>query().eq(ApiDocConstants.DB_MODIFY_FROM_KEY, id));
        return SimpleResultUtils.createSimpleResult(infoDetail)
                .add("components", (Serializable) infoDetails)
                .add("historyCount", historyCount);
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") Integer id) {
        ApiProjectInfoDetail infoDetail = apiProjectInfoDetailService.getById(id);
        if (infoDetail == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProject project = apiProjectService.getById(infoDetail.getProjectId());
        if (!apiGroupService.checkProjectAccess(getLoginUser(), project, ApiGroupAuthority.DELETABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectInfoDetailService.removeById(id));
    }

    @DeleteMapping("/removeByIds/{ids}")
    public SimpleResult<Boolean> removeByIds(@PathVariable("ids") List<Integer> ids) {
        List<ApiProjectInfoDetail> infoDetails = apiProjectInfoDetailService.listByIds(ids);
        List<Integer> projectIds = infoDetails.stream().map(ApiProjectInfoDetail::getProjectId).distinct().collect(Collectors.toList());
        List<ApiProject> apiProjects = apiProjectService.listByIds(projectIds);
        for (ApiProject apiProject : apiProjects) {
            if (!apiGroupService.checkProjectAccess(getLoginUser(), apiProject, ApiGroupAuthority.DELETABLE)) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
            }
        }
        return SimpleResultUtils.createSimpleResult(apiProjectInfoDetailService.removeByIds(ids));
    }

    @PostMapping
    public SimpleResult<ApiProjectInfoDetail> save(@RequestBody ApiProjectInfoDetail infoDetail) {
        ApiProject project = apiProjectService.getById(infoDetail.getProjectId());
        if (!apiGroupService.checkProjectAccess(getLoginUser(), project, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        ApiProjectInfoDetail existsInfoDetail = null;
        if (infoDetail.getId() != null) {
            existsInfoDetail = apiProjectInfoDetailService.getById(infoDetail.getId());
            if (existsInfoDetail != null && SimpleModelUtils.isSameData(infoDetail, existsInfoDetail, "schemaContent")
                    && ApiSchemaContentUtils.isSameSchemaContent(infoDetail.getSchemaContent(), existsInfoDetail.getSchemaContent())) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2000);
            }
        }
        if (infoDetail.getInfoId() == null) {
            ApiProjectInfo projectInfo = apiProjectService.findOrCreateProjectInfo(infoDetail.getProjectId());
            if (projectInfo != null) {
                infoDetail.setInfoId(projectInfo.getId());
            }
        }
        if (apiProjectInfoDetailService.existsInfoDetail(infoDetail)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        if (infoDetail.getVersion() == null) {
            infoDetail.setVersion(1);
        }
        if (existsInfoDetail != null) {
            if (existsInfoDetail.getVersion() == null) {
                existsInfoDetail.setVersion(1);
            }
            apiProjectInfoDetailService.saveApiHistory(existsInfoDetail);
        }
        apiProjectInfoDetailService.saveOrUpdate(SimpleModelUtils.addAuditInfo(infoDetail));
        return SimpleResultUtils.createSimpleResult(infoDetail);
    }

    /**
     * 获取历史版本
     *
     * @param queryVo
     * @return
     */
    @PostMapping("/historyList")
    public SimpleResult<List<ApiProjectInfoDetail>> loadHistoryList(@RequestBody SimpleQueryVo queryVo) {
        Integer infoDetailId = queryVo.getQueryId();
        Page<ApiProjectInfoDetail> page = SimpleResultUtils.toPage(queryVo);
        ApiProjectInfoDetail currentDoc = apiProjectInfoDetailService.getById(infoDetailId);
        if (currentDoc == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectInfoDetailService.page(page, Wrappers.<ApiProjectInfoDetail>query()
                        .eq(ApiDocConstants.DB_MODIFY_FROM_KEY, infoDetailId)
                        .orderByDesc("data_version")))
                .add("current", currentDoc);
    }

    /**
     * 获取历史版本
     *
     * @param queryVo
     * @return
     */
    @PostMapping("/loadHistoryDiff")
    public SimpleResult<Map<String, ApiProjectInfoDetail>> loadHistoryDiff(@RequestBody ApiDocHistoryQueryVo queryVo) {
        Integer id = queryVo.getQueryId();
        Integer maxVersion = queryVo.getVersion();
        ApiProjectInfoDetail modified = apiProjectInfoDetailService.getById(id);
        Page<ApiProjectInfoDetail> page = new Page<>(1, 2);
        apiProjectInfoDetailService.page(page, Wrappers.<ApiProjectInfoDetail>query().eq(ApiDocConstants.DB_MODIFY_FROM_KEY, ObjectUtils.defaultIfNull(modified.getModifyFrom(), modified.getId()))
                .le(maxVersion != null, "data_version", maxVersion)
                .orderByDesc("data_version"));
        if (page.getRecords().isEmpty()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        } else {
            Map<String, ApiProjectInfoDetail> map = new HashMap<>(2);
            List<ApiProjectInfoDetail> dataList = page.getRecords();
            map.put("modifiedDoc", modified);
            dataList.stream().filter(data -> !data.getId().equals(modified.getId())).findFirst().ifPresent(data -> {
                map.put("originalDoc", data);
            });
            return SimpleResultUtils.createSimpleResult(map);
        }
    }

    @PostMapping("/recoverFromHistory")
    public SimpleResult<ApiProjectInfoDetail> recoverFromHistory(@RequestBody ApiDocHistoryQueryVo historyVo) {
        ApiProjectInfoDetail history = apiProjectInfoDetailService.getById(historyVo.getQueryId()); // 加载历史
        ApiProjectInfoDetail target = null;
        if (history != null && history.getModifyFrom() != null) {
            target = apiProjectInfoDetailService.getById(history.getModifyFrom());
        }
        if (history == null || target == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProjectInfoDetail apiInfoDetail = apiProjectInfoDetailService.copyFromHistory(history, target);
        return this.save(apiInfoDetail); // 更新
    }

    @PostMapping("/copyApiModel/{id}")
    public SimpleResult<ApiProjectInfoDetail> copyApiModel(@PathVariable("id") Integer id) { // 模型复制
        ApiProjectInfoDetail infoDetail = apiProjectInfoDetailService.getById(id);
        if (infoDetail == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!apiProjectService.validateUserProject(infoDetail.getProjectId())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return apiProjectInfoDetailService.copyApiModel(infoDetail);
    }
}
