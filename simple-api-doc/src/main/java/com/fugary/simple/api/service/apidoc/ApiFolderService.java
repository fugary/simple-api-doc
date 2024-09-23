package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiFolderService extends IService<ApiFolder> {
    /**
     * 保存数据
     *
     * @param apiFolders
     * @param project
     * @param parentFolder
     * @return
     */
    int saveApiFolders(List<? extends ApiFolder> apiFolders, ApiProject project, ApiFolder parentFolder);

    /**
     * 获取根目录
     *
     * @param projectId
     * @return
     */
    ApiFolder getRootFolder(Integer projectId);

    /**
     * 创建根目录
     *
     * @param project
     * @return
     */
    ApiFolder createRootFolder(ApiProject project);
}
