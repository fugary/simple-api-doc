package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectSchemaDetail;
import com.fugary.simple.api.mapper.api.ApiProjectSchemaDetailMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectSchemaDetailService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectSchemaDetailVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Create date 2024/9/26<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectSchemaDetailServiceImpl extends ServiceImpl<ApiProjectSchemaDetailMapper, ApiProjectSchemaDetail> implements ApiProjectSchemaDetailService {

    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectSchemaDetail>query().eq("project_id", projectId));
    }

    @Override
    public void saveApiProjectSchema(List<ExportApiProjectSchemaDetailVo> projectSchemaDetails, ApiProject apiProject) {
        deleteByProject(apiProject.getId());
        projectSchemaDetails.forEach(projectSchemaDetail -> {
            projectSchemaDetail.setProjectId(apiProject.getId());
            save(SimpleModelUtils.addAuditInfo(projectSchemaDetail));
        });
    }
}
