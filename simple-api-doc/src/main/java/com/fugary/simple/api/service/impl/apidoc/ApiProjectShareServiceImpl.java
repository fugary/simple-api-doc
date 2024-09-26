package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.mapper.api.ApiProjectShareMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import org.springframework.stereotype.Service;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectShareServiceImpl extends ServiceImpl<ApiProjectShareMapper, ApiProjectShare> implements ApiProjectShareService {
    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectShare>query().eq("project_id", projectId));
    }
}
