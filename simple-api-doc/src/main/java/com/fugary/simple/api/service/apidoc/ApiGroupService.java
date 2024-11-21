package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiUserGroup;
import com.fugary.simple.api.web.vo.user.ApiGroupVo;
import com.fugary.simple.api.web.vo.user.ApiUserGroupVo;

import java.util.List;

/**
 * Create date 2024/11/20<br>
 *
 * @author gary.fu
 */
public interface ApiGroupService extends IService<ApiGroup> {

    /**
     * 检查是否有重复
     *
     * @param group
     * @return
     */
    boolean existsGroup(ApiGroup group);

    /**
     * 加载分组
     *
     * @param groupCode
     * @return
     */
    ApiGroup loadGroup(String groupCode);

    /**
     * 加载用户支持的组
     *
     * @param userId
     * @return
     */
    List<ApiGroupVo> loadUserGroups(Integer userId);

    /**
     * 加载组用户
     *
     * @param groupCode
     * @return
     */
    List<ApiUserGroup> loadGroupUsers(String groupCode);

    /**
     * 删除用户支持的组
     *
     * @param userId
     * @return
     */
    int deleteUserGroupsByUid(Integer userId);

    /**
     * 删除组关联用户
     *
     * @param groupCode
     * @return
     */
    int deleteUserGroupsByCode(String groupCode);

    /**
     * 保存用户分组权限
     * @param userGroupVo
     * @return
     */
    boolean saveUserGroups(ApiUserGroupVo userGroupVo);
}
