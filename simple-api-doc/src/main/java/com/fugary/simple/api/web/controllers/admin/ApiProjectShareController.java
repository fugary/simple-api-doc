package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.project.AdminProjectShareVo;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private ApiProjectAccessService apiProjectAccessService;

    @GetMapping
    public SimpleResult<List<ApiProjectShare>> search(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiProjectShare> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        String groupCode = StringUtils.trimToEmpty(queryVo.getGroupCode());
        if (!apiProjectAccessService.canAccessGroup(groupCode, ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        QueryWrapper<ApiProjectShare> queryWrapper = Wrappers.<ApiProjectShare>query()
                .eq(queryVo.getProjectId() != null, "project_id", queryVo.getProjectId())
                .and(StringUtils.isNotBlank(keyword), w -> w.like("share_name", keyword).or().like("description", keyword))
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .orderByDesc("id");
        if (Boolean.TRUE.equals(queryVo.getOnlyMine())) {
            String loginUserName = SecurityUtils.getLoginUserName();
            queryWrapper.eq("creator", loginUserName);
            if (StringUtils.isNotBlank(groupCode)) {
                queryWrapper.exists("select 1 from t_api_project p where p.id = t_api_project_share.project_id and p.group_code={0}", groupCode);
            }
        } else {
            addGroupCodeQuery(queryVo, queryWrapper, userName);
        }
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
        if (SecurityUtils.isAdmin() && StringUtils.isNotBlank(queryVo.getUserName())) {
            queryWrapper.eq("creator", queryVo.getUserName());
            if (StringUtils.isNotBlank(queryVo.getGroupCode())) {
                queryWrapper.exists("select 1 from t_api_project p where p.id = t_api_project_share.project_id and p.group_code={0}", queryVo.getGroupCode());
            }
        } else {
            apiProjectAccessService.addProjectRelatedGroupCodeQuery(queryWrapper, "t_api_project_share", "project_id", queryVo.getGroupCode(), userName);
        }
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiProjectShare> get(@PathVariable("id") Integer id) {
        ApiProjectShare apiShare = apiProjectShareService.getById(id);
        if (apiShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!apiProjectAccessService.canAccessShare(apiShare, ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiShare);
    }

    @DeleteMapping("/{id}")
    public SimpleResult remove(@PathVariable("id") Integer id) {
        ApiProjectShare apiShare = apiProjectShareService.getById(id);
        if (apiShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!apiProjectAccessService.canAccessShare(apiShare, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.removeById(id));
    }

    @PostMapping
    public SimpleResult save(@RequestBody ApiProjectShare apiShare) {
        ApiProjectShare existsShare = null;
        if (apiShare.getId() != null) {
            existsShare = apiProjectShareService.getById(apiShare.getId());
            if (existsShare == null) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
            }
            if (!apiProjectAccessService.canAccessShare(existsShare, ApiGroupAuthority.WRITABLE)) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
            }
        }
        if (!apiProjectAccessService.canAccessShare(apiShare, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        apiShare.setShareId(StringUtils.defaultIfBlank(apiShare.getShareId(), SimpleModelUtils.uuid()));
        if (existsShare != null && SimpleModelUtils.isSameData(apiShare, existsShare)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2000, existsShare);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiShare)));
    }

    @PostMapping("/copy/{id}")
    public SimpleResult<ApiProjectShare> copy(@PathVariable("id") Integer id) {
        ApiProjectShare projectShare = apiProjectShareService.getById(id);
        if (projectShare == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!apiProjectAccessService.canAccessShare(projectShare, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiProjectShareService.copyProjectShare(projectShare));
    }

}
