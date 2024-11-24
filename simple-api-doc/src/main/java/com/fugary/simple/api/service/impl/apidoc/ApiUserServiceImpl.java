package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.mapper.api.ApiUserMapper;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.LoginResultVo;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created on 2020/5/3 22:37 .<br>
 *
 * @author gary.fu
 */
@Service
public class ApiUserServiceImpl extends ServiceImpl<ApiUserMapper, ApiUser> implements ApiUserService {

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiGroupService apiGroupService;

    @Override
    public ApiUserVo loadUser(String userName) {
        ApiUser apiUser = getOne(Wrappers.<ApiUser>query().eq("user_name", userName));
        ApiUserVo userVo = SimpleModelUtils.copy(apiUser, ApiUserVo.class);
        if (userVo != null) {
            List<ApiGroupVo> groups = apiGroupService.loadUserGroups(userVo);
            userVo.setGroups(groups);
        }
        return userVo;
    }

    @Override
    public boolean deleteUser(Integer id) {
        getOptById(id).ifPresent(mockUser -> {
            apiProjectService.list(Wrappers.<ApiProject>query().eq("user_name", mockUser.getUserName()))
                    .forEach(apiProject -> {
                        apiProjectService.deleteApiProject(apiProject.getId());
                    });
            apiGroupService.deleteUserGroupsByUid(id);
        });
        return removeById(id);
    }

    @Override
    public boolean existsUser(ApiUser user) {
        List<ApiUser> exists = list(Wrappers.<ApiUser>query().eq("user_name", user.getUserName()));
        return exists.stream().anyMatch(existGroup -> !existGroup.getId().equals(user.getId()));
    }

    @Override
    public String encryptPassword(String password) {
        return DigestUtils.sha256Hex(password);
    }

    @Override
    public boolean matchPassword(String password, String encryptPassword) {
        return StringUtils.equalsIgnoreCase(encryptPassword(password), encryptPassword);
    }
}
