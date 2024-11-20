package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.web.vo.user.ApiUserVo;

/**
 * Created on 2020/5/5 22:36 .<br>
 *
 * @author gary.fu
 */
public interface ApiUserService extends IService<ApiUser> {

    /**
     * 加载用户Vo
     * @param userName
     * @return
     */
    ApiUserVo loadUser(String userName);

    /**
     * 级联删除分组和请求和数据
     *
     * @param id
     * @return
     */
    boolean deleteUser(Integer id);

    /**
     * 检查是否有重复
     *
     * @param user
     * @return
     */
    boolean existsUser(ApiUser user);

    /**
     * 加密密码
     * @param password
     * @return
     */
    String encryptPassword(String password);

    /**
     * 密码匹配
     * @param password
     * @param encryptPassword
     * @return
     */
    boolean matchPassword(String password, String encryptPassword);
}
