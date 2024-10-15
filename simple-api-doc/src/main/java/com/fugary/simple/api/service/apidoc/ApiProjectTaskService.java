package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

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
     * @param foldersMap
     * @return
     */
    int copyProjectTasks(Integer fromProjectId, Integer toProjectId, Integer id,
                         Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap);
}
