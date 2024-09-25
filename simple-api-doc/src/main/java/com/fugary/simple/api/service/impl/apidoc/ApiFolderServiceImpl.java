package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.mapper.api.ApiFolderMapper;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiFolderServiceImpl extends ServiceImpl<ApiFolderMapper, ApiFolder> implements ApiFolderService {

    @Override
    public int saveApiFolders(List<? extends ApiFolder> apiFolders, ApiProject project, ApiFolder parentFolder) {
        if (parentFolder == null) {
            parentFolder = getRootFolder(project.getId());
            if (parentFolder == null) {
                parentFolder = createRootFolder(project);
            }
        }
        // TODO 和保存数据库的folder信息比较
        ApiFolder finalParentFolder = parentFolder;
        saveOrUpdateBatch(apiFolders.stream().map(folder -> {
            folder.setParentId(finalParentFolder.getId());
            folder.setProjectId(project.getId());
            ApiFolder apiFolder = new ApiFolder();
            BeanUtils.copyProperties(folder, apiFolder);
            return apiFolder;
        }).collect(Collectors.toList()));
        return apiFolders.size();
    }

    @Override
    public ApiFolder getRootFolder(Integer projectId) {
        QueryWrapper<ApiFolder> queryWrapper = Wrappers.<ApiFolder>query()
                .eq("project_id", projectId)
                .eq("root_flag", true);
        return getOne(queryWrapper);
    }

    @Override
    public ApiFolder createRootFolder(ApiProject project) {
        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setProjectId(project.getId());
        rootFolder.setRootFlag(true);
        rootFolder.setFolderName("根目录");
        rootFolder.setStatus(ApiDocConstants.STATUS_ENABLED);
        save(SimpleModelUtils.addAuditInfo(rootFolder));
        return rootFolder;
    }
}
