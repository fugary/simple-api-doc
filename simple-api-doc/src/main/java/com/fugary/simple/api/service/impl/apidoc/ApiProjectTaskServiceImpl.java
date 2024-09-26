package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProjectTask;
import com.fugary.simple.api.mapper.api.ApiProjectTaskMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectTaskService;
import org.springframework.stereotype.Service;

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
}
