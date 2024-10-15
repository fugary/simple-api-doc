package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.mapper.api.ApiProjectTaskMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectTaskServiceImpl extends ServiceImpl<ApiProjectTaskMapper, ApiProjectTask> implements ApiProjectTaskService {
    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectTask>query().eq("project_id", projectId));
    }

    @Override
    public int copyProjectTasks(Integer fromProjectId, Integer toProjectId, Integer id,
                                Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap) {
        List<ApiProjectTask> tasks = list(Wrappers.<ApiProjectTask>query()
                .eq("project_id", fromProjectId)
                .eq(id != null, "id", id));
        tasks.forEach(task -> {
            task.setId(null);
            task.setProjectId(toProjectId);
            if (fromProjectId.equals(toProjectId)) {
                task.setTaskName(task.getTaskName() + "-copy");
            }
            Pair<ApiFolder, ApiFolder> folderPair = foldersMap.get(task.getToFolder());
            ApiFolder newFolder = folderPair.getRight();
            if (newFolder != null) {
                task.setToFolder(newFolder.getId());
            }
            save(task);
        });
        return tasks.size();
    }
}
