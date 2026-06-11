package com.fugary.simple.api.service.impl.apidoc;

import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectAccessService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

import static com.fugary.simple.api.utils.security.SecurityUtils.getLoginUser;

@Service
public class ApiProjectAccessServiceImpl implements ApiProjectAccessService {

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiGroupService apiGroupService;

    @Autowired
    private ApiUserService apiUserService;

    @Override
    public boolean canAccessProject(Integer projectId, ApiGroupAuthority authority) {
        return apiProjectService.validateUserProject(projectId, authority);
    }

    @Override
    public boolean canAccessProject(ApiProject project, ApiGroupAuthority authority) {
        return apiGroupService.checkProjectAccess(getLoginUser(), project, authority);
    }

    @Override
    public boolean canAccessGroup(String groupCode, ApiGroupAuthority authority) {
        return StringUtils.isBlank(groupCode) || apiGroupService.checkGroupAccess(getLoginUser(), groupCode, authority);
    }

    @Override
    public String loadReadableGroupCodesSql(String userName) {
        ApiUserVo apiUser = apiUserService.loadUser(userName);
        if (apiUser == null) {
            return "";
        }
        return apiUser.getGroups().stream()
                .filter(group -> apiGroupService.checkGroupAccess(apiUser, group.getGroupCode(), ApiGroupAuthority.READABLE))
                .map(ApiGroup::getGroupCode)
                .filter(StringUtils::isNotBlank)
                .map(groupCode -> StringUtils.replace(groupCode, "'", "''"))
                .collect(Collectors.joining("','"));
    }
}
