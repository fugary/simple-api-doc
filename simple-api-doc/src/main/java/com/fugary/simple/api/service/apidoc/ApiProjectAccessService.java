package com.fugary.simple.api.service.apidoc;

import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.project.AdminProjectShareVo;
import com.fugary.simple.api.web.vo.task.SimpleTaskVo;
import org.apache.commons.lang3.StringUtils;

public interface ApiProjectAccessService {

    boolean canAccessProject(Integer projectId, ApiGroupAuthority authority);

    boolean canAccessProject(ApiProject project, ApiGroupAuthority authority);

    boolean canAccessGroup(String groupCode, ApiGroupAuthority authority);

    String loadReadableGroupCodesSql(String userName);

    default boolean canAccessDoc(ApiDoc apiDoc, ApiGroupAuthority authority) {
        return apiDoc != null && canAccessProject(apiDoc.getProjectId(), authority);
    }

    default boolean canAccessFolder(ApiFolder apiFolder, ApiGroupAuthority authority) {
        return apiFolder != null && canAccessProject(apiFolder.getProjectId(), authority);
    }

    default boolean canAccessInfoDetail(ApiProjectInfoDetail infoDetail, ApiGroupAuthority authority) {
        return infoDetail != null && canAccessProject(infoDetail.getProjectId(), authority);
    }

    default boolean canAccessTask(ApiProjectTask apiTask, ApiGroupAuthority authority) {
        return apiTask != null && canAccessProject(apiTask.getProjectId(), authority);
    }

    default boolean canAccessShare(ApiProjectShare apiShare, ApiGroupAuthority authority) {
        return apiShare != null && canAccessProject(apiShare.getProjectId(), authority);
    }

    default AdminProjectShareVo maskSharePassword(AdminProjectShareVo shareVo) {
        if (shareVo != null) {
            shareVo.setHasPassword(StringUtils.isNotBlank(shareVo.getSharePassword()));
            if (!canAccessShare(shareVo, ApiGroupAuthority.WRITABLE)) {
                shareVo.setSharePassword(null);
            }
        }
        return shareVo;
    }

    default boolean canAccessSimpleTask(SimpleTaskVo taskVo, ApiGroupAuthority authority) {
        return taskVo != null && (taskVo.getProjectId() == null
                ? SecurityUtils.isAdmin()
                : canAccessProject(taskVo.getProjectId(), authority));
    }

    default boolean canAccessImportGroup(String groupCode, ApiGroupAuthority authority) {
        if (StringUtils.isBlank(groupCode)) {
            return true;
        }
        ApiProject apiProject = new ApiProject();
        apiProject.setGroupCode(groupCode);
        return canAccessProject(apiProject, authority);
    }

    /**
     * Add project-related group code query for query wrappers
     * @param queryWrapper Query Wrapper
     * @param tableName Main table name of the query (e.g. t_api_project_share)
     * @param projectIdColumn The foreign key column for project_id
     * @param groupCode Group code to query
     * @param userName User name to filter by
     * @param <T> Entity type
     */
    default <T> void addProjectRelatedGroupCodeQuery(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<T> queryWrapper,
                                                     String tableName, String projectIdColumn,
                                                     String groupCode, String userName) {
        if (StringUtils.isNotBlank(groupCode)) {
            queryWrapper.exists(String.format("select 1 from t_api_project p where p.id = %s.%s and p.group_code={0}", tableName, projectIdColumn), groupCode);
        } else if (StringUtils.isNotBlank(userName)) {
            String groupCodesStr = loadReadableGroupCodesSql(userName);
            String projectGroupSql = String.format("select 1 from t_api_project p where p.id = %s.%s and p.group_code in ('%s')", tableName, projectIdColumn, groupCodesStr);
            String projectUserSql = String.format("select 1 from t_api_project p where p.id = %s.%s and p.user_name={0} and (p.group_code is null or p.group_code = '')", tableName, projectIdColumn);
            queryWrapper.and(wrapper -> wrapper.exists(StringUtils.isNotBlank(groupCodesStr), projectGroupSql)
                    .or().exists(projectUserSql, userName));
        }
    }

    /**
     * Add group code query for Project Query Wrapper
     * @param queryWrapper Query Wrapper
     * @param groupCode Group code to query
     * @param userName User name to filter by
     * @param <T> Entity type
     */
    default <T> void addProjectGroupCodeQuery(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<T> queryWrapper,
                                              String groupCode, String userName) {
        if (StringUtils.isNotBlank(groupCode)) {
            queryWrapper.eq("group_code", groupCode);
        } else if (StringUtils.isNotBlank(userName)) {
            String groupCodesStr = loadReadableGroupCodesSql(userName);
            queryWrapper.and(wrapper -> wrapper.exists(StringUtils.isNotBlank(groupCodesStr), "select 1 from t_api_group g where g.group_code = t_api_project.group_code and g.group_code in ('" + groupCodesStr + "')")
                    .or().eq("user_name", userName));
        }
    }
}
