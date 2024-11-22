package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiUserGroup;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import com.fugary.simple.api.web.vo.user.ApiUserGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping("/admin/groups")
public class ApiGroupController {

    @Autowired
    private ApiGroupService apiGroupService;

    @Autowired
    private ApiUserService apiUserService;

    @GetMapping("/loadProjectGroups")
    public SimpleResult<List<ApiGroup>> loadProjectGroups(@ModelAttribute ProjectQueryVo queryVo) {
        queryVo.setUserName(StringUtils.defaultIfBlank(queryVo.getUserName(), SecurityUtils.getLoginUserName()));
        List<ApiGroup> apiGroups;
        if (SecurityUtils.isAdmin(queryVo.getUserName())) {
            apiGroups = apiGroupService.list(Wrappers.<ApiGroup>query().eq("status", ApiDocConstants.STATUS_ENABLED));
        } else {
            ApiUserVo userVo = apiUserService.loadUser(queryVo.getUserName());
            apiGroups = apiGroupService.loadUserGroups(userVo.getId()).stream()
                    .map(vo -> SimpleModelUtils.copy(vo, ApiGroup.class)).collect(Collectors.toList());
        }
        return SimpleResultUtils.createSimpleResult(apiGroups);
    }

    @PostMapping("/loadGroupUsers/{groupCode}")
    public SimpleResult<List<ApiUserGroup>> loadGroupUsers(@PathVariable("groupCode") String groupCode) {
        ApiGroup apiGroup = apiGroupService.loadGroup(groupCode);
        if (apiGroup == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.loadGroupUsers(groupCode))
                .add("group", apiGroup);
    }

    @PostMapping("/saveUserGroups")
    public SimpleResult<Boolean> saveUserGroups(@RequestBody ApiUserGroupVo userGroupVo) {
        ApiGroup apiGroup = apiGroupService.loadGroup(userGroupVo.getGroupCode());
        if (apiGroup == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.saveUserGroups(userGroupVo));
    }

    @GetMapping
    public SimpleResult<List<ApiGroup>> search(@ModelAttribute SimpleQueryVo queryVo) {
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        Page<ApiGroup> page = SimpleResultUtils.toPage(queryVo);
        QueryWrapper<ApiGroup> queryWrapper = Wrappers.query();
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like("group_name", keyword)
                    .or().like("description", keyword));
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.page(page, queryWrapper));
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiGroup> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiGroupService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") Integer id) {
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.removeById(id));
    }

    @PostMapping
    public SimpleResult<Boolean> save(@RequestBody ApiGroup group) {
        if (apiGroupService.existsGroup(group)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        if (!SecurityUtils.isAdmin()) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (StringUtils.isBlank(group.getGroupCode())) {
            group.setGroupCode(SimpleModelUtils.uuid());
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.saveOrUpdate(SimpleModelUtils.addAuditInfo(group)));
    }
}
