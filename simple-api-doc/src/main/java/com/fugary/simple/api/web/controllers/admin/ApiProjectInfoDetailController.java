package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectComponentQueryVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
                .eq("body_type", queryVo.getBodyType());
        return SimpleResultUtils.createSimpleResult(apiProjectInfoDetailService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProjectInfoDetail> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiProjectInfoDetailService.getById(id));
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
        List<ApiProjectInfoDetail> infoDetails = apiProjectInfoDetailService.lambdaQuery()
                .eq(ApiProjectInfoDetail::getSchemaName, infoDetail.getSchemaName()).list();
        for (ApiProjectInfoDetail detail : infoDetails) {
            detail.setSchemaContent(infoDetail.getSchemaContent());
            detail.setDescription(infoDetail.getDescription());
        }
        apiProjectInfoDetailService.saveOrUpdate(SimpleModelUtils.addAuditInfo(infoDetail));
        return SimpleResultUtils.createSimpleResult(infoDetail);
    }
}
