package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProjectTask;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiProjectTaskService extends IService<ApiProjectTask> {
    /**
     * 按照projectId删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);

    /**
     * 复制数据
     *
     * @param fromProjectId
     * @param toProjectId
     * @param id
     * @return
     */
    int copyProjectTasks(Integer fromProjectId, Integer toProjectId, Integer id);
}
