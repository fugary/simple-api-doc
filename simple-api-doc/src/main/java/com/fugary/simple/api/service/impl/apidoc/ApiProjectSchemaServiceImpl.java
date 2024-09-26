package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectSchema;
import com.fugary.simple.api.mapper.api.ApiProjectSchemaMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectSchemaService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectSchemaVo;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Created on 2020/5/3 22:37 .<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectSchemaServiceImpl extends ServiceImpl<ApiProjectSchemaMapper, ApiProjectSchema> implements ApiProjectSchemaService {

    @Override
    public ApiProjectSchema loadByProjectId(Integer projectId) {
        return getOne(Wrappers.<ApiProjectSchema>query().eq("project_id", projectId));
    }

    @Override
    public void saveApiProjectSchema(ExportApiProjectSchemaVo projectSchemaVo, ApiProject apiProject, boolean importExists) {
        if (projectSchemaVo != null) {
            ApiProjectSchema existsProjectSchema;
            Integer projectId = apiProject.getId();
            if (importExists && (existsProjectSchema = loadByProjectId(projectId)) != null
                    && Objects.equals(projectId, existsProjectSchema.getId())) {
                projectSchemaVo.setId(existsProjectSchema.getId());
            }
            projectSchemaVo.setProjectId(projectId);
            save(SimpleModelUtils.addAuditInfo(projectSchemaVo));
        }
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectSchema>query().eq("project_id", projectId));
    }
}
