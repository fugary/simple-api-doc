package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiUserGroup;
import com.fugary.simple.api.mapper.api.ApiGroupMapper;
import com.fugary.simple.api.mapper.api.ApiUserGroupMapper;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserGroupVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
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

    @Override
    public boolean existsGroup(ApiGroup group) {
        List<ApiGroup> exists = list(Wrappers.<ApiGroup>query()
                .eq("group_code", group.getGroupCode()).or().eq("group_name", group.getGroupName()));
        return exists.stream().anyMatch(existGroup -> !existGroup.getId().equals(group.getId()));
    }

    @Override
    public List<ApiGroupVo> loadUserGroups(Integer userId) {
        QueryWrapper<ApiGroup> queryWrapper = Wrappers.<ApiGroup>query().eq("status", ApiDocConstants.STATUS_ENABLED)
                .exists("select 1 from t_api_user_group g where g.group_code = t_api_group.group_code and g.user_id={0}", userId);
        List<ApiGroup> groups = list(queryWrapper);
        List<ApiGroupVo> results = new ArrayList<>();
        if (!groups.isEmpty()) {
            List<ApiUserGroup> userGroups = apiUserGroupMapper.selectList(Wrappers.<ApiUserGroup>query().eq("user_id", userId));
            Map<String, List<ApiUserGroup>> userGroupMap = userGroups.stream().collect(Collectors.groupingBy(ApiUserGroup::getGroupCode));
            for (ApiGroup group : groups) {
                ApiGroupVo groupVo = SimpleModelUtils.copy(group, ApiGroupVo.class);
                groupVo.setAuthorities(userGroupMap.getOrDefault(group.getGroupCode(), new ArrayList<>()).stream()
                        .map(ApiUserGroup::getAuthorities).filter(StringUtils::isNotBlank)
                        .flatMap(authority -> Arrays.stream(authority.split(",")).distinct()).collect(Collectors.toList()));
                results.add(groupVo);
            }
        }
        return results;
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
}
