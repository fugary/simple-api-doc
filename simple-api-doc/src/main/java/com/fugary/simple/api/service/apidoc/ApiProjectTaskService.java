package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
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
    Map<Integer, ApiProjectTask> copyProjectTasks(Integer fromProjectId, Integer toProjectId, Integer id,
                                          Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap);

    /**
     * 复制一份导入任务
     *
     * @param projectTask
     * @return
     */
    SimpleResult<ApiProjectTask> copyProjectTask(ApiProjectTask projectTask);

    /**
     * 把url类型导入保存为任务，方便下次使用
     *
     * @param importVo
     * @param importResult
     * @return
     */
    SimpleResult<ApiProject> saveUrlImportAsTask(ApiProjectImportVo importVo, SimpleResult<ApiProject> importResult);
}
