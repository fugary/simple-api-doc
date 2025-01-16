package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.AdminProjectShareVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fugary.simple.api.utils.security.SecurityUtils.getLoginUser;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/shares")
public class ApiProjectShareController {

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiGroupService apiGroupService;

    @Autowired
    private ApiUserService apiUserService;

    @GetMapping
    public SimpleResult<List<ApiProjectShare>> search(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiProjectShare> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        String groupCode = StringUtils.trimToEmpty(queryVo.getGroupCode());
        if (StringUtils.isNotBlank(groupCode)
                && !apiGroupService.checkGroupAccess(getLoginUser(), groupCode, ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        QueryWrapper<ApiProjectShare> queryWrapper = Wrappers.<ApiProjectShare>query()
                .eq(queryVo.getProjectId() != null, "project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "share_name", keyword)
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus());;
        addGroupCodeQuery(queryVo, queryWrapper, userName);
        Page<ApiProjectShare> pageResult = apiProjectShareService.page(page, queryWrapper);
        if (!pageResult.getRecords().isEmpty()) {
            Map<Integer, ApiProject> projectMap = apiProjectService.list(Wrappers.<ApiProject>query().in("id",
                            pageResult.getRecords().stream().map(ApiProjectShare::getProjectId).collect(Collectors.toList())))
                    .stream().collect(Collectors.toMap(ApiProject::getId, Function.identity()));
            List<ApiProjectShare> shareList = pageResult.getRecords().stream().map(share -> {
                AdminProjectShareVo shareVo = SimpleModelUtils.copy(share, AdminProjectShareVo.class);
                shareVo.setProject(projectMap.get(shareVo.getProjectId()));
                return shareVo;
            }).collect(Collectors.toList());
            pageResult.setRecords(shareList);
        }
        return SimpleResultUtils.createSimpleResult(pageResult);
    }

    /**
     * 添加项目查询sql
     *
     * @param queryVo
     * @param queryWrapper
     * @param userName
     */
    protected void addGroupCodeQuery(ProjectQueryVo queryVo, QueryWrapper<ApiProjectShare> queryWrapper, String userName) {
        if (StringUtils.isNotBlank(queryVo.getGroupCode())) {
            queryWrapper.exists("select 1 from t_api_project p where p.id = t_api_project_share.project_id and p.group_code={0}", queryVo.getGroupCode());
        } else {
            ApiUserVo apiUser = apiUserService.loadUser(userName);
            String groupCodesStr = apiUser.getGroups().stream().map(ApiGroup::getGroupCode)
                    .filter(StringUtils::isNotBlank).collect(Collectors.joining("','"));
            queryWrapper.and(wrapper -> wrapper.exists(StringUtils.isNotBlank(groupCodesStr), "select 1 from t_api_project p where p.id = t_api_project_share.project_id and p.group_code in ('" + groupCodesStr + "')")
                    .or().exists("select 1 from t_api_project p where p.id = t_api_project_share.project_id and p.user_name={0} and (p.group_code is null or p.group_code = '')", userName));
        }
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProjectShare> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult remove(@PathVariable("id") Integer id) {
        ApiProjectShare apiShare = apiProjectShareService.getById(id);
        if (!validateShareUser(apiShare)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.removeById(id));
    }

    @PostMapping
    public SimpleResult save(@RequestBody ApiProjectShare apiShare) {
        if (!validateShareUser(apiShare)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (!validateShareUser(apiShare)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        apiShare.setShareId(StringUtils.defaultIfBlank(apiShare.getShareId(), SimpleModelUtils.uuid()));
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiShare)));
    }

    @PostMapping("/copy/{id}")
    public SimpleResult<ApiProjectShare> copy(@PathVariable("id") Integer id) {
        ApiProjectShare projectShare = apiProjectShareService.getById(id);
        if (projectShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        ApiProject apiProject = apiProjectService.getById(projectShare.getProjectId());
        if (!apiGroupService.checkProjectAccess(getLoginUser(), apiProject, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.copyProjectShare(projectShare));
    }

    protected boolean validateShareUser(ApiProjectShare projectShare) {
        if (projectShare != null) {
            return apiProjectService.validateUserProject(projectShare.getProjectId());
        }
        return true;
    }

}
