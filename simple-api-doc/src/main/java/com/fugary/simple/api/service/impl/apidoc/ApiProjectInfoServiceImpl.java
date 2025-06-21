package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.mapper.api.ApiProjectInfoMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created on 2020/5/3 22:37 .<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectInfoServiceImpl extends ServiceImpl<ApiProjectInfoMapper, ApiProjectInfo> implements ApiProjectInfoService {

    @Autowired
    private ApiProjectInfoDetailService apiProjectInfoDetailService;

    @Override
    public List<ApiProjectInfo> loadByProjectId(Integer projectId) {
        return list(Wrappers.<ApiProjectInfo>query().eq("project_id", projectId));
    }

    @Override
    public ApiProjectInfo loadByProjectId(Integer projectId, Integer folderId) {
        return getOne(Wrappers.<ApiProjectInfo>query().eq("project_id", projectId)
                .eq("folder_id", folderId));
    }

    @Override
    public ApiProjectInfo loadByFolderId(Integer folderId) {
        return getOne(Wrappers.<ApiProjectInfo>query().eq("folder_id", folderId));
    }

    @Override
    public ApiProjectInfo saveApiProjectInfo(ExportApiProjectInfoVo projectInfoVo, ApiProject apiProject, ApiFolder mountFolder, boolean importExists) {
        if (projectInfoVo != null) {
            projectInfoVo.setProjectId(apiProject.getId());
            projectInfoVo.setFolderId(mountFolder.getId());
            ApiProjectInfo existsProjectInfo;
            if (importExists && (existsProjectInfo = loadByProjectId(projectInfoVo.getProjectId(), projectInfoVo.getFolderId())) != null) {
                List<ExportEnvConfigVo> mergedEnvConfigs = ApiDocParseUtils.mergeEnvConfigs(existsProjectInfo.getEnvContent(), projectInfoVo.getEnvContent());
                SimpleModelUtils.copyNoneNullValue(existsProjectInfo, projectInfoVo);
                existsProjectInfo.setEnvContent(JsonUtils.toJson(mergedEnvConfigs));
            }
            saveOrUpdate(SimpleModelUtils.addAuditInfo(projectInfoVo));
        }
        return projectInfoVo;
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        apiProjectInfoDetailService.deleteByProject(projectId);
        return this.remove(Wrappers.<ApiProjectInfo>query().eq("project_id", projectId));
    }

    @Override
    public boolean deleteByFolder(Integer folderId) {
        ApiProjectInfo apiProjectInfo = loadByFolderId(folderId);
        if (apiProjectInfo != null) {
            apiProjectInfoDetailService.deleteByProjectInfo(apiProjectInfo.getProjectId(), apiProjectInfo.getId());
        }
        return this.remove(Wrappers.<ApiProjectInfo>query().eq("folder_id", folderId));
    }

    @Override
    public Map<Integer, Pair<ApiProjectInfo, ApiProjectInfo>> copyProjectInfos(Integer fromProjectId, Integer toProjectId,
                                                                               Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap) {
        List<ApiProjectInfo> projectInfos = loadByProjectId(fromProjectId);
        Map<Integer, Pair<ApiProjectInfo, ApiProjectInfo>> results = new HashMap<>();
        projectInfos.forEach(projectInfo -> {
            ApiProjectInfo newProjectInfo = SimpleModelUtils.copy(projectInfo, ApiProjectInfo.class);
            newProjectInfo.setId(null);
            newProjectInfo.setProjectId(toProjectId);
            Pair<ApiFolder, ApiFolder> folderPair = foldersMap.get(projectInfo.getFolderId());
            if (folderPair != null && folderPair.getRight() != null) {
                newProjectInfo.setFolderId(folderPair.getRight().getId());
            }
            save(newProjectInfo);
            results.put(projectInfo.getId(), Pair.of(projectInfo, newProjectInfo));
            List<ApiProjectInfoDetail> infoDetails = apiProjectInfoDetailService.loadByProjectAndInfo(fromProjectId,
                    projectInfo.getId(), Set.of());
            infoDetails.forEach(infoDetail -> {
                infoDetail.setId(null);
                infoDetail.setProjectId(toProjectId);
                infoDetail.setInfoId(newProjectInfo.getId());
                apiProjectInfoDetailService.save(infoDetail);
            });
        });
        return results;
    }
}
