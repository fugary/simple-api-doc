package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiGroup;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiUser;
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
     * 加载组用户
     *
     * @param groupCodes
     * @return
     */
    List<ApiUserGroup> loadGroupUsers(List<String> groupCodes);

    /**
     * 加载组用户
     *
     * @param userId
     * @param groupCode
     * @return
     */
    List<ApiUserGroup> loadGroupUsers(Integer userId, String groupCode);

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

    /**
     * 校验用户分组权限
     * @param apiUser
     * @param groupCode
     * @param apiAuthority
     * @return
     */
    boolean checkGroupAccess(ApiUser apiUser, String groupCode, ApiGroupAuthority apiAuthority);

    /**
     * 校验用户分组权限
     * @param apiUser
     * @param apiGroup
     * @param apiAuthority
     * @return
     */
    boolean checkGroupAccess(ApiUser apiUser, ApiGroup apiGroup, ApiGroupAuthority apiAuthority);

    /**
     * 校验用户分组权限
     * @param apiUser
     * @param apiProject
     * @return
     */
    boolean checkProjectAccess(ApiUser apiUser, ApiProject apiProject, ApiGroupAuthority apiAuthority);

}
