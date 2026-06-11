package com.fugary.simple.api.service.apidoc;

import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.utils.security.SecurityUtils;
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
}
