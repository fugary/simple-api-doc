package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.mapper.api.ApiFolderMapper;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiFolderServiceImpl extends ServiceImpl<ApiFolderMapper, ApiFolder> implements ApiFolderService {

    @Autowired
    private ApiDocService apiDocService;

    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @Override
    public ApiFolder getOrCreateMountFolder(ApiProject project, ApiFolder parentFolder) {
        if (parentFolder == null && (parentFolder = getRootFolder(project.getId())) == null) {
            parentFolder = createRootFolder(project);
        }
        return parentFolder;
    }

    @Override
    public int saveApiFolders(List<ExportApiFolderVo> apiFolders, ApiProject project, ApiFolder parentFolder, List<ExportApiDocVo> extraDocs) {
        parentFolder = getOrCreateMountFolder(project, parentFolder);
        Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair = calcFolderMap(loadApiFolders(project.getId()));
        Map<Integer, String> folderPathMap = folderMapPair.getValue();
        Map<String, Pair<String, ApiDoc>> existsDocMap = calcDocMap(apiDocService.loadByProject(project.getId()), folderPathMap);
        ApiFolder mountFolder = parentFolder; // 挂载目录
        apiFolders.forEach(folder -> saveApiFolderVo(mountFolder, folder, folderMapPair, existsDocMap));
        this.saveApiDocs(mountFolder, null, extraDocs, folderMapPair, existsDocMap);
        return apiFolders.size();
    }

    protected Map<String, Pair<String, ApiDoc>> calcDocMap(List<ApiDoc> docs, Map<Integer, String> folderPathMap) {
        Map<String, Pair<String, ApiDoc>> docMap = new HashMap<>();
        docs.forEach(doc -> {
            String folderPath = folderPathMap.get(doc.getFolderId());
            docMap.put(doc.getDocKey(), Pair.of(folderPath, doc));
        });
        return docMap;
    }

    protected void saveApiFolderVo(ApiFolder mountFolder, ExportApiFolderVo folder,
                                   Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair,
                                   Map<String, Pair<String, ApiDoc>> existsDocMap) {
        Map<String, ApiFolder> pathFolderMap = folderMapPair.getKey();
        Map<Integer, String> folderPathMap = folderMapPair.getValue();
        String folderPath = folderPathMap.get(mountFolder.getId()) + "/" + folder.getFolderPath();
        ApiFolder existsFolder = pathFolderMap.get(folderPath);
        if (existsFolder == null) {
            folder.setParentId(mountFolder.getId());
            folder.setProjectId(mountFolder.getProjectId());
            save(SimpleModelUtils.addAuditInfo(folder));
            existsFolder = folder;
        } else {
            updateById(SimpleModelUtils.addAuditInfo(existsFolder)); // 更新
        }
        saveApiDocs(mountFolder, folder, folder.getDocs(), folderMapPair, existsDocMap);
        if (!CollectionUtils.isEmpty(folder.getFolders())) { // 子目录
            for (ExportApiFolderVo subFolder : folder.getFolders()) {
                saveApiFolderVo(existsFolder, subFolder, folderMapPair, existsDocMap);
            }
        }
    }

    /**
     * 保存doc信息
     *
     * @param mountFolder   挂载目录
     * @param folder        父级目录
     * @param docs          文档列表
     * @param folderMapPair 文档解析map
     * @param existsDocMap  已保存文档map
     */
    protected void saveApiDocs(ApiFolder mountFolder, ExportApiFolderVo folder, List<ExportApiDocVo> docs,
                               Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair,
                               Map<String, Pair<String, ApiDoc>> existsDocMap) {
        Map<Integer, String> folderPathMap = folderMapPair.getRight();
        if (!CollectionUtils.isEmpty(docs)) {
            String folderPath = folderPathMap.get(mountFolder.getId());
            Integer projectId = mountFolder.getProjectId();
            Integer folderId = mountFolder.getId();
            if (folder != null) {
                folderPath = folderPath + "/" + folder.getFolderPath(); // 子目录挂载上去
                folderId = folder.getId();
            }
            // 保存folder docs
            for (ExportApiDocVo apiDocVo : docs) {
                Pair<String, ApiDoc> existsDocPair = existsDocMap.get(apiDocVo.getDocKey());
                if (existsDocPair != null && StringUtils.equals(existsDocPair.getKey(), folderPath)
                        && existsDocPair.getValue() != null) { // 目录相同
                    apiDocVo.setId(existsDocPair.getValue().getId());
                    apiDocSchemaService.deleteByDoc(apiDocVo.getId());
                }
                apiDocVo.setProjectId(projectId);
                apiDocVo.setFolderId(folderId);
                apiDocService.saveOrUpdate(SimpleModelUtils.addAuditInfo(apiDocVo));
                saveApiDocSchemas(apiDocVo);
            }
        }
    }

    protected void saveApiDocSchemas(ExportApiDocVo apiDocVo) {
        if (apiDocVo.getParametersSchema() != null) {
            apiDocVo.getParametersSchema().setDocId(apiDocVo.getId());
            apiDocSchemaService.save(SimpleModelUtils.addAuditInfo(apiDocVo.getParametersSchema()));
        }
        if (!CollectionUtils.isEmpty(apiDocVo.getRequestsSchemas())) {
            apiDocSchemaService.saveBatch(apiDocVo.getRequestsSchemas().stream().map(requestSchema -> {
                requestSchema.setDocId(apiDocVo.getId());
                return SimpleModelUtils.addAuditInfo(requestSchema);
            }).collect(Collectors.toList()));
        }
        if (!CollectionUtils.isEmpty(apiDocVo.getResponsesSchemas())) {
            apiDocSchemaService.saveBatch(apiDocVo.getResponsesSchemas().stream().map(responseSchema -> {
                responseSchema.setDocId(apiDocVo.getId());
                return SimpleModelUtils.addAuditInfo(responseSchema);
            }).collect(Collectors.toList()));
        }
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
        rootFolder.setSortId(0);
        save(SimpleModelUtils.addAuditInfo(rootFolder));
        return rootFolder;
    }

    @Override
    public List<ApiFolder> loadApiFolders(Integer projectId) {
        return list(Wrappers.<ApiFolder>query().eq("project_id", projectId));
    }

    @Override
    public List<ApiFolder> loadEnabledApiFolders(Integer projectId) {
        return list(Wrappers.<ApiFolder>query().eq("project_id", projectId)
                .eq("status", ApiDocConstants.STATUS_ENABLED));
    }

    @Override
    public Pair<Map<String, ApiFolder>, Map<Integer, String>> calcFolderMap(List<ApiFolder> apiFolders) {
        Map<Integer, ApiFolder> folderMap = apiFolders.stream().collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
        Map<String, ApiFolder> pathFolderMap = new HashMap<>();
        Map<Integer, String> folderPathMap = new HashMap<>();
        for (ApiFolder apiFolder : apiFolders) {
            List<String> paths = new ArrayList<>();
            paths.add(apiFolder.getFolderName());
            while (apiFolder.getParentId() != null) {
                apiFolder = folderMap.get(apiFolder.getParentId());
                paths.add(0, apiFolder.getFolderName());
            }
            String folderPath = String.join("/", paths);
            pathFolderMap.put(folderPath, apiFolder);
            folderPathMap.put(apiFolder.getId(), folderPath);
        }
        return Pair.of(pathFolderMap, folderPathMap);
    }

    @Override
    public boolean deleteFolder(Integer folderId) {
        apiDocService.deleteByFolder(folderId);
        return this.removeById(folderId);
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        apiDocService.deleteByProject(projectId);
        return this.remove(Wrappers.<ApiFolder>query().eq("project_id", projectId));
    }
}
