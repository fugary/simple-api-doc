package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.entity.api.ApiUserGroup;
import com.fugary.simple.api.mapper.api.ApiGroupMapper;
import com.fugary.simple.api.mapper.api.ApiUserGroupMapper;
import com.fugary.simple.api.mapper.api.ApiUserMapper;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserGroupVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
@Service
public class ApiGroupServiceImpl extends ServiceImpl<ApiGroupMapper, ApiGroup> implements ApiGroupService {

    @Autowired
    private ApiUserGroupMapper apiUserGroupMapper;

    @Autowired
    private ApiUserMapper apiUserMapper;

    @Override
    public boolean existsGroup(ApiGroup group) {
        List<ApiGroup> exists = list(Wrappers.<ApiGroup>query()
                .eq("group_code", group.getGroupCode()).or().eq("group_name", group.getGroupName()));
        return exists.stream().anyMatch(existGroup -> !existGroup.getId().equals(group.getId()));
    }

    @Override
    public ApiGroup loadGroup(String groupCode) {
        return getOne(Wrappers.<ApiGroup>query().eq("group_code", groupCode), false);
    }

    @Override
    public List<ApiGroupVo> loadUserGroups(ApiUser apiUser) {
        Integer userId = apiUser.getId();
        String userName = apiUser.getUserName();
        QueryWrapper<ApiGroup> queryWrapper = Wrappers.<ApiGroup>query().eq("status", ApiDocConstants.STATUS_ENABLED)
                .and(wrapper -> wrapper.exists("select 1 from t_api_user_group g where g.group_code = t_api_group.group_code and g.user_id={0}", userId)
                        .or().eq("user_name", userName));
        List<ApiGroup> groups = list(queryWrapper);
        List<ApiGroupVo> results = new ArrayList<>();
        if (!groups.isEmpty()) {
            List<ApiUserGroup> userGroups = apiUserGroupMapper.selectList(Wrappers.<ApiUserGroup>query().eq("user_id", userId));
            Map<String, List<ApiUserGroup>> userGroupMap = userGroups.stream().collect(Collectors.groupingBy(ApiUserGroup::getGroupCode));
            for (ApiGroup group : groups) {
                ApiGroupVo groupVo = SimpleModelUtils.copy(group, ApiGroupVo.class);
                List<ApiUserGroup> groupUsers = userGroupMap.getOrDefault(group.getGroupCode(), new ArrayList<>());
                if (!groupUsers.isEmpty()) {
                    groupVo.setAuthorities(groupUsers.get(0).getAuthorities());
                    groupVo.setUserGroups(groupUsers);
                }
                results.add(groupVo);
            }
        }
        return results;
    }

    @Override
    public List<ApiUserGroup> loadGroupUsers(String groupCode) {
        return apiUserGroupMapper.selectList(Wrappers.<ApiUserGroup>query().eq("group_code", groupCode));
    }

    @Override
    public List<ApiUserGroup> loadGroupUsers(Integer userId, String groupCode) {
        return apiUserGroupMapper.selectList(Wrappers.<ApiUserGroup>query().eq("group_code", groupCode)
                .eq("user_id", userId));
    }

    @Override
    public List<ApiUserGroup> loadGroupUsers(List<String> groupCodes) {
        if (CollectionUtils.isEmpty(groupCodes)) {
            return new ArrayList<>();
        }
        return apiUserGroupMapper.selectList(Wrappers.<ApiUserGroup>query().in("group_code", groupCodes));
    }

    @Override
    public int deleteUserGroupsByUid(Integer userId) {
        return apiUserGroupMapper.delete(Wrappers.<ApiUserGroup>query().eq("user_id", userId));
    }

    @Override
    public int deleteUserGroupsByCode(String groupCode) {
        return apiUserGroupMapper.delete(Wrappers.<ApiUserGroup>query().eq("group_code", groupCode));
    }

    @Override
    public boolean saveUserGroups(ApiUserGroupVo userGroupVo) {
        deleteUserGroupsByCode(userGroupVo.getGroupCode());
        userGroupVo.getUserGroups().forEach(userGroup -> {
            if (StringUtils.isBlank(userGroup.getGroupCode())) {
                userGroup.setGroupCode(userGroupVo.getGroupCode());
            }
            apiUserGroupMapper.insert(userGroup);
        });
        return true;
    }

    @Override
    public boolean checkGroupAccess(ApiUser apiUser, String groupCode, ApiGroupAuthority apiAuthority) {
        if (apiUser == null || StringUtils.isBlank(groupCode)) {
            return false;
        }
        return checkGroupAccess(apiUser, loadGroup(groupCode), apiAuthority);
    }

    @Override
    public boolean checkGroupAccess(ApiUser apiUser, ApiGroup apiGroup, ApiGroupAuthority apiAuthority) {
        if (apiUser == null || apiGroup == null) {
            return false;
        }
        if (SecurityUtils.isAdmin(apiUser.getUserName()) || StringUtils.equals(apiUser.getUserName(), apiGroup.getUserName())) {
            return true;
        }
        List<ApiUserGroup> userGroups = loadGroupUsers(apiUser.getId(), apiGroup.getGroupCode());
        if (userGroups.isEmpty() || StringUtils.isBlank(userGroups.get(0).getAuthorities())) {
            return false;
        }
        return ApiGroupAuthority.checkAccess(userGroups.get(0).getAuthorities(), apiAuthority);
    }

    @Override
    public boolean checkProjectAccess(ApiUser apiUser, ApiProject apiProject, ApiGroupAuthority apiAuthority) {
        if (apiUser == null || apiProject == null) {
            return false;
        }
        if (SecurityUtils.isAdmin(apiUser.getUserName())) {
            return true;
        }
        if (StringUtils.isBlank(apiProject.getGroupCode())) {
            return StringUtils.equals(apiUser.getUserName(), apiProject.getUserName());
        }
        return checkGroupAccess(apiUser, apiProject.getGroupCode(), apiAuthority);
    }
}
