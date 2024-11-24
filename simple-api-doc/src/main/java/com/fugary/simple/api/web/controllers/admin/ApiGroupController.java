package com.fugary.simple.api.web.controllers.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.entity.api.ApiUserGroup;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.query.ProjectQueryVo;
import com.fugary.simple.api.web.vo.query.SimpleQueryVo;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private ApiProjectService apiProjectService;

    @GetMapping("/loadProjectGroups")
    public SimpleResult<List<ApiGroupVo>> loadProjectGroups(@ModelAttribute ProjectQueryVo queryVo) {
        queryVo.setUserName(StringUtils.defaultIfBlank(queryVo.getUserName(), SecurityUtils.getLoginUserName()));
        List<ApiGroupVo> apiGroups;
        if (SecurityUtils.isAdmin(queryVo.getUserName())) {
            List<ApiGroup> adminGroups = apiGroupService.list(Wrappers.<ApiGroup>query().eq("status", ApiDocConstants.STATUS_ENABLED));
            apiGroups = adminGroups.stream().map(group -> SimpleModelUtils.copy(group, ApiGroupVo.class)).collect(Collectors.toList());
        } else {
            ApiUserVo userVo = apiUserService.loadUser(queryVo.getUserName());
            apiGroups = apiGroupService.loadUserGroups(userVo.getId()).stream()
                    .filter(apiGroupVo -> apiGroupService.checkGroupAccess(userVo, apiGroupVo.getGroupCode(), ApiGroupAuthority.READABLE))
                    .collect(Collectors.toList());
        }
        return SimpleResultUtils.createSimpleResult(apiGroups);
    }

    @PostMapping("/loadGroupUsers/{groupCode}")
    public SimpleResult<List<ApiUserGroup>> loadGroupUsers(@PathVariable("groupCode") String groupCode) {
        ApiGroup apiGroup = apiGroupService.loadGroup(groupCode);
        if (apiGroup == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!SecurityUtils.validateUserUpdate(apiGroup.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
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
        if (!SecurityUtils.validateUserUpdate(apiGroup.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.saveUserGroups(userGroupVo));
    }

    @GetMapping
    public SimpleResult<List<ApiGroupVo>> search(@ModelAttribute SimpleQueryVo queryVo) {
        Page<ApiGroup> page = SimpleResultUtils.toPage(queryVo);
        String keyword = StringUtils.trimToEmpty(queryVo.getKeyword());
        String userName = SecurityUtils.getUserName(queryVo.getUserName());
        QueryWrapper<ApiGroup> queryWrapper = Wrappers.<ApiGroup>query()
                .eq(queryVo.getStatus() != null, "status", queryVo.getStatus())
                .eq("user_name", userName)
                .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper.like("group_name", keyword)
                        .or().like("description", keyword));
        Page<ApiGroup> groupPage = apiGroupService.page(page, queryWrapper);
        List<String> groupCodes = groupPage.getRecords().stream().map(ApiGroup::getGroupCode).distinct().collect(Collectors.toList());
        List<ApiUserGroup> apiUserGroups = apiGroupService.loadGroupUsers(groupCodes);
        Map<String, List<ApiUserGroup>> groupUsersMap = apiUserGroups.stream().collect(Collectors.groupingBy(ApiUserGroup::getGroupCode));
        List<Integer> userIds = apiUserGroups.stream().map(ApiUserGroup::getUserId).distinct().collect(Collectors.toList());
        List<ApiGroupVo> apiGroups = groupPage.getRecords().stream().map(group -> {
            ApiGroupVo groupVo = SimpleModelUtils.copy(group, ApiGroupVo.class);
            List<ApiUserGroup> userGroups = groupUsersMap.getOrDefault(group.getGroupCode(), new ArrayList<>());
            if (!userGroups.isEmpty()) {
                groupVo.setAuthorities(userGroups.get(0).getAuthorities());
            }
            groupVo.setUserGroups(userGroups);
            return groupVo;
        }).collect(Collectors.toList());
        List<ApiUser> apiUsers = apiUserService.listByIds(userIds);
        return SimpleResultUtils.createSimpleResult(apiGroups)
                .add("users", (Serializable) apiUsers);
    }

    @GetMapping("/{id}")
    public SimpleResult<ApiGroup> get(@PathVariable("id") Integer id) {
        return SimpleResultUtils.createSimpleResult(apiGroupService.getById(id));
    }

    @DeleteMapping("/{id}")
    public SimpleResult<Boolean> remove(@PathVariable("id") Integer id) {
        ApiGroup apiGroup = apiGroupService.getOne(Wrappers.<ApiGroup>query().eq("id", id));
        if (apiGroup == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404);
        }
        if (!SecurityUtils.validateUserUpdate(apiGroup.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        apiGroupService.deleteUserGroupsByCode(apiGroup.getGroupCode());
        apiProjectService.update(Wrappers.<ApiProject>update().eq("group_code", apiGroup.getGroupCode())
                .set("group_code", ""));
        return SimpleResultUtils.createSimpleResult(apiGroupService.removeById(id));
    }

    @PostMapping
    public SimpleResult<Boolean> save(@RequestBody ApiGroup group) {
        if (apiGroupService.existsGroup(group)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_1001);
        }
        if (!SecurityUtils.validateUserUpdate(group.getUserName())) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        if (StringUtils.isBlank(group.getGroupCode())) {
            group.setGroupCode(SimpleModelUtils.uuid());
        }
        return SimpleResultUtils.createSimpleResult(apiGroupService.saveOrUpdate(SimpleModelUtils.addAuditInfo(group)));
    }
}
