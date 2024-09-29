package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.mapper.api.ApiProjectInfoDetailMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoDetailVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Create date 2024/9/26<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectInfoDetailServiceImpl extends ServiceImpl<ApiProjectInfoDetailMapper, ApiProjectInfoDetail> implements ApiProjectInfoDetailService {

    @Override
    public List<ApiProjectInfoDetail> loadByProjectAndInfo(Integer projectId, Integer infoId, Set<String> types) {
        return this.list(Wrappers.<ApiProjectInfoDetail>query().eq("project_id", projectId)
                .eq("info_id", infoId).in(!types.isEmpty(), "body_type", types));
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectInfoDetail>query().eq("project_id", projectId));
    }

    @Override
    public boolean deleteByProjectInfo(Integer projectId, Integer infoId) {
        return this.remove(Wrappers.<ApiProjectInfoDetail>query().eq("project_id", projectId)
                .eq("info_id", infoId));
    }

    @Override
    public void saveApiProjectInfoDetails(ApiProject apiProject, ApiProjectInfo apiProjectInfo, List<ExportApiProjectInfoDetailVo> projectInfoDetails) {
        deleteByProjectInfo(apiProject.getId(), apiProjectInfo.getId());
        projectInfoDetails.forEach(projectInfoDetailVo -> {
            projectInfoDetailVo.setProjectId(apiProject.getId());
            projectInfoDetailVo.setInfoId(apiProjectInfo.getId());
            save(SimpleModelUtils.addAuditInfo(projectInfoDetailVo));
        });
    }
}
