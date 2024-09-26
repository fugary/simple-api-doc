package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.mapper.api.ApiUserMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiUserService;
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

    @Override
    public boolean deleteMockUser(Integer id) {
        getOptById(id).ifPresent(mockUser -> {
            apiProjectService.list(Wrappers.<ApiProject>query().eq("user_name", mockUser.getUserName()))
                    .forEach(apiProject -> {
                        apiProjectService.deleteApiProject(apiProject.getId());
                    });
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
