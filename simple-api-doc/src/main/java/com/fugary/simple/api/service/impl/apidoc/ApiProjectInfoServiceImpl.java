package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.mapper.api.ApiProjectInfoMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created on 2020/5/3 22:37 .<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectInfoServiceImpl extends ServiceImpl<ApiProjectInfoMapper, ApiProjectInfo> implements ApiProjectInfoService {

    @Override
    public List<ApiProjectInfo> listByProjectId(Integer projectId) {
        return list(Wrappers.<ApiProjectInfo>query().eq("project_id", projectId));
    }

    @Override
    public ApiProjectInfo loadByProjectId(Integer projectId, Integer folderId) {
        return getOne(Wrappers.<ApiProjectInfo>query().eq("project_id", projectId)
                .eq("folder_id", folderId));
    }

    @Override
    public ApiProjectInfo saveApiProjectInfo(ExportApiProjectInfoVo projectInfoVo, ApiProject apiProject, ApiFolder mountFolder, boolean importExists) {
        if (projectInfoVo != null) {
            ApiProjectInfo existsProjectInfo;
            if (importExists && (existsProjectInfo = loadByProjectId(projectInfoVo.getProjectId(), projectInfoVo.getFolderId())) != null) {
                SimpleModelUtils.copyNoneNullValue(existsProjectInfo, projectInfoVo);
            }
            projectInfoVo.setProjectId(apiProject.getId());
            projectInfoVo.setFolderId(mountFolder.getId());
            save(SimpleModelUtils.addAuditInfo(projectInfoVo));
        }
        return projectInfoVo;
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectInfo>query().eq("project_id", projectId));
    }
}
