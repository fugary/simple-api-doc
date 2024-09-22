package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.mapper.api.ApiProjectMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Create date 2024/7/15<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectServiceImpl extends ServiceImpl<ApiProjectMapper, ApiProject> implements ApiProjectService {

//    @Autowired
//    private MockGroupService mockGroupService;

    @Override
    public boolean deleteMockProject(Integer id) {
        ApiProject mockProject = getById(id);
        if (mockProject != null) {
//            List<ModelGroup> mockGroups = mockGroupService.list(Wrappers.<ModelGroup>query()
//                    .eq("project_code", mockProject.getProjectCode())
//                    .eq("user_name", mockProject.getUserName()));
//            mockGroupService.deleteMockGroups(mockGroups.stream().map(ModelGroup::getId).collect(Collectors.toList()));
        }
        return removeById(id);
    }

    @Override
    public boolean deleteMockProjects(List<Integer> ids) {
        for (Integer id : ids) {
            deleteMockProject(id);
        }
        return false;
    }

    @Override
    public boolean existsMockProject(ApiProject project) {
//        if (ApiDocConstants.MOCK_DEFAULT_PROJECT.equalsIgnoreCase(project.getProjectCode())) {
//            return true;
//        }
        List<ApiProject> existProjects = list(Wrappers.<ApiProject>query().eq("user_name", project.getUserName())
                .eq("project_code", project.getProjectCode()));
        return existProjects.stream().anyMatch(existProject -> !existProject.getId().equals(project.getId()));
    }
}
