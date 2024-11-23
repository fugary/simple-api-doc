package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
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

    @GetMapping
    public SimpleResult<List<ApiProjectShare>> search(@ModelAttribute ProjectQueryVo queryVo) {
        Page<ApiProjectShare> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        String groupCode = StringUtils.trimToEmpty(queryVo.getGroupCode());
        if (StringUtils.isNotBlank(queryVo.getGroupCode())
                && !apiGroupService.checkGroupAccess(getLoginUser(), groupCode, ApiGroupAuthority.READABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        QueryWrapper<ApiProjectShare> queryWrapper = Wrappers.<ApiProjectShare>query()
                .eq(queryVo.getProjectId() != null, "project_id", queryVo.getProjectId())
                .like(StringUtils.isNotBlank(keyword), "share_name", keyword)
                .exists(StringUtils.isBlank(groupCode), "select 1 from t_api_project p where p.id = t_api_project_share.project_id and p.user_name={0} and (p.group_code is null or p.group_code = '')", userName)
                .exists(StringUtils.isNotBlank(groupCode), "select 1 from t_api_project p where p.id = t_api_project_share.project_id and p.group_code={0}", groupCode);
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

    protected boolean validateShareUser(ApiProjectShare projectShare) {
        if (projectShare != null) {
            return apiProjectService.validateUserProject(projectShare.getProjectId());
        }
        return true;
    }

}
