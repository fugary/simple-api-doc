package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.mapper.api.ApiFolderMapper;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import com.fugary.simple.api.web.vo.project.ApiDocConfigSortsVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiDocSortVo;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;

import java.util.*;
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

    @Autowired
    private ApiProjectInfoService apiProjectInfoService;

    private PathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public ApiFolder getOrCreateMountFolder(ApiProject project, ApiFolder parentFolder) {
        if (parentFolder == null && (parentFolder = getRootFolder(project.getId())) == null) {
            parentFolder = createRootFolder(project);
        }
        return parentFolder;
    }

    @Override
    public int saveApiFolders(ApiProject project, ApiProjectInfo projectInfo, ApiFolder mountFolder, List<ExportApiFolderVo> apiFolders, List<ExportApiDocVo> extraDocs) {
        mountFolder = getOrCreateMountFolder(project, mountFolder);
        Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair = calcFolderMap(loadApiFolders(project.getId()));
        Map<Integer, String> folderPathMap = folderMapPair.getValue();
        Map<String, Pair<String, ApiDoc>> existsDocMap = calcDocMap(apiDocService.loadByProject(project.getId()), folderPathMap);
        ApiFolder finalMountFolder = mountFolder; // 挂载目录
        List<ApiFolder> newFolders = new ArrayList<>();
        List<ApiDoc> apiDocs = new ArrayList<>();
        apiFolders.forEach(folder -> saveApiFolderVo(projectInfo, finalMountFolder, finalMountFolder, folder, folderMapPair, existsDocMap, newFolders, apiDocs));
        this.saveApiDocs(projectInfo, mountFolder, null, extraDocs, existsDocMap, apiDocs);
        removeFoldersWithoutDocs(folderMapPair, newFolders, apiDocs);
        return apiFolders.size();
    }

    protected void removeFoldersWithoutDocs(Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair, List<ApiFolder> newFolders, List<ApiDoc> apiDocs){
        Map<Integer, List<ApiFolder>> childFoldersMap = calcChildFoldersMap(folderMapPair.getLeft().values());
        Set<Integer> foldersToDelete = new HashSet<>();
        newFolders.forEach(folder -> {
            int docCount = calcFolderDocCount(childFoldersMap, folder, apiDocs);
            if (docCount == 0) {
                foldersToDelete.add(folder.getId());
            }
        });
        removeByIds(foldersToDelete);
    }

    protected Map<Integer, List<ApiFolder>> calcChildFoldersMap(Collection<ApiFolder> folders) {
        return folders.stream().filter(folder -> Objects.nonNull(folder.getParentId())).collect(Collectors.groupingBy(ApiFolder::getParentId));
    }

    protected int calcFolderDocCount(Map<Integer, List<ApiFolder>> childFoldersMap, ApiFolder parentFolder, List<ApiDoc> apiDocs) {
        // 直接计算当前文件夹的文档数量
        int docCount = (int) apiDocs.stream().filter(doc -> parentFolder.getId().equals(doc.getFolderId())).count();
        // 获取当前文件夹的子文件夹
        List<ApiFolder> subFolders = childFoldersMap.getOrDefault(parentFolder.getId(), Collections.emptyList());
        // 遍历子文件夹，递归累加文档数量
        for (ApiFolder folder : subFolders) {
            docCount += calcFolderDocCount(childFoldersMap, folder, apiDocs);
        }
        return docCount;
    }


    protected Map<String, Pair<String, ApiDoc>> calcDocMap(List<ApiDoc> docs, Map<Integer, String> folderPathMap) {
        Map<String, Pair<String, ApiDoc>> docMap = new HashMap<>();
        docs.forEach(doc -> {
            String folderPath = folderPathMap.get(doc.getFolderId());
            docMap.put(calcDocKey(doc.getInfoId(), doc), Pair.of(folderPath, doc));
        });
        return docMap;
    }

    protected String calcDocKey(Integer infoId, ApiDoc apiDoc) {
        String infoIdStr = String.valueOf(infoId);
        return StringUtils.join(List.of(infoIdStr, apiDoc.getDocKey()), "__");
    }

    protected void saveApiFolderVo(ApiProjectInfo projectInfo, ApiFolder rootFolder, ApiFolder parentFolder, ExportApiFolderVo folder,
                                   Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair,
                                   Map<String, Pair<String, ApiDoc>> existsDocMap,
                                   List<ApiFolder> newFolders, List<ApiDoc> apiDocs) {
        Map<String, ApiFolder> pathFolderMap = folderMapPair.getKey();
        Map<Integer, String> folderPathMap = folderMapPair.getValue();
        String folderPath = folderPathMap.get(rootFolder.getId()) + "/" + folder.getFolderPath();
        ApiFolder existsFolder = pathFolderMap.get(folderPath);
        if (existsFolder == null) {
            folder.setParentId(parentFolder.getId());
            folder.setProjectId(parentFolder.getProjectId());
            save(SimpleModelUtils.addAuditInfo(folder));
            newFolders.add(folder);
            existsFolder = folder;
        } else {
            SimpleModelUtils.copyNoneNullValue(existsFolder, folder);
            folder.setStatus(existsFolder.getStatus());
            folder.setSortId(existsFolder.getSortId());
            updateById(SimpleModelUtils.addAuditInfo(folder)); // 更新
        }
        pathFolderMap.put(folderPath, folder);
        folderPathMap.put(folder.getId(), folderPath);
        saveApiDocs(projectInfo, parentFolder, folder, folder.getDocs(), existsDocMap, apiDocs);
        if (!CollectionUtils.isEmpty(folder.getFolders())) { // 子目录
            for (ExportApiFolderVo subFolder : folder.getFolders()) {
                saveApiFolderVo(projectInfo, rootFolder, existsFolder, subFolder, folderMapPair, existsDocMap, newFolders, apiDocs);
            }
        }
    }

    /**
     * 保存doc信息
     *
     * @param projectInfo 项目基本信息
     * @param parentFolder   挂载目录
     * @param folder        父级目录
     * @param docs          文档列表
     * @param existsDocMap  已保存文档map
     * @param apiDocs  保存的apiDocs信息
     */
    protected void saveApiDocs(ApiProjectInfo projectInfo, ApiFolder parentFolder,
                               ExportApiFolderVo folder, List<ExportApiDocVo> docs,
                               Map<String, Pair<String, ApiDoc>> existsDocMap, List<ApiDoc> apiDocs) {
        if (!CollectionUtils.isEmpty(docs)) {
            Integer projectId = parentFolder.getProjectId();
            Integer folderId = parentFolder.getId();
            if (folder != null) {
                folderId = folder.getId();
            }
            // 保存folder docs
            for (ExportApiDocVo apiDocVo : docs) {
                Pair<String, ApiDoc> existsDocPair = existsDocMap.get(calcDocKey(projectInfo.getId(), apiDocVo));
                apiDocVo.setDocVersion(1);
                apiDocVo.setInfoId(projectInfo.getId());
                apiDocVo.setProjectId(projectId);
                apiDocVo.setFolderId(folderId);
                ApiDoc existsDoc = null;
                boolean locked = false;
                ApiDocDetailVo existsDocDetail = null;
                if (existsDocPair != null && existsDocPair.getValue() != null) { // 文档存在
                    existsDoc = existsDocPair.getValue();
                    apiDocVo.setId(existsDoc.getId());
                    apiDocVo.setStatus(existsDoc.getStatus());
                    apiDocVo.setDocVersion(existsDoc.getDocVersion());
                    apiDocVo.setSortId(existsDoc.getSortId());
                    apiDocVo.setFolderId(existsDoc.getFolderId());
                    processModifiedApiDoc(apiDocVo, existsDoc);
                    locked = Boolean.TRUE.equals(existsDoc.getLocked());
                    if (!locked) {
                        existsDocDetail = apiDocSchemaService.loadDetailVo(apiDocVo);
                        apiDocSchemaService.deleteByDoc(apiDocVo.getId());
                    }
                }
                if (ApiDocConstants.DOC_TYPE_API.equals(apiDocVo.getDocType())) {
                    apiDocVo.setSpecVersion(projectInfo.getSpecVersion());
                }
                if (!locked) {
                    boolean schemaChanged = ApiDocParseUtils.processExistsSchemas(apiDocVo, existsDocDetail);
                    apiDocService.saveApiDoc(SimpleModelUtils.addAuditInfo(apiDocVo), existsDoc, schemaChanged);
                    saveApiDocSchemas(apiDocVo);
                }
                apiDocs.add(apiDocVo);
            }
        }
    }

    protected void processModifiedApiDoc(ExportApiDocVo apiDocVo, ApiDoc existsDoc) {
        if (existsDoc != null && ApiDocConstants.DOC_TYPE_API.equals(existsDoc.getDocType())) {
            if (!StringUtils.equals(existsDoc.getDocName(), existsDoc.getSummary())) { // 修改过docName
                apiDocVo.setDocName(existsDoc.getDocName());
            }
            if (StringUtils.isNotBlank(existsDoc.getDocContent())) { // 修改过docContent
                apiDocVo.setDocContent(existsDoc.getDocContent());
            }
        }
    }

    protected void saveApiDocSchemas(ExportApiDocVo apiDocVo) {
        if (apiDocVo.getSecurityRequirements() != null) {
            apiDocVo.getSecurityRequirements().setDocId(apiDocVo.getId());
            apiDocSchemaService.save(SimpleModelUtils.addAuditInfo(apiDocVo.getSecurityRequirements()));
        }
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
        return list(Wrappers.<ApiFolder>query().eq("project_id", projectId).orderByAsc("sort_id"));
    }

    @Override
    public List<ApiFolder> loadSubFolders(Integer folderId) {
        ApiFolder apiFolder = getById(folderId);
        if (apiFolder != null) {
            List<ApiFolder> allFolders = loadApiFolders(apiFolder.getProjectId());
            Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair = calcFolderMap(allFolders);
            Map<Integer, String> folderPathMap = folderMapPair.getRight();
            Map<String, ApiFolder> pathFolderMap = folderMapPair.getLeft();
            String path = folderPathMap.get(apiFolder.getId());
            return pathFolderMap.entrySet().stream()
                    .filter(entry-> pathMatcher.match(path + "/**", entry.getKey())).map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    public List<ApiFolder> loadEnabledApiFolders(Integer projectId) {
        return list(Wrappers.<ApiFolder>query().eq("project_id", projectId)
                .eq(ApiDocConstants.STATUS_KEY, ApiDocConstants.STATUS_ENABLED)
                .orderByAsc("sort_id"));
    }

    @Override
    public Pair<Map<String, ApiFolder>, Map<Integer, String>> calcFolderMap(List<ApiFolder> apiFolders) {
        Map<Integer, ApiFolder> folderMap = apiFolders.stream().collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
        Map<String, ApiFolder> pathFolderMap = new HashMap<>();
        Map<Integer, String> folderPathMap = new HashMap<>();
        for (ApiFolder apiFolder : apiFolders) {
            List<String> paths = new ArrayList<>();
            paths.add(apiFolder.getFolderName());
            ApiFolder currentFolder = apiFolder;
            while (currentFolder != null && currentFolder.getParentId() != null) {
                currentFolder = folderMap.get(currentFolder.getParentId());
                if (currentFolder != null) {
                    paths.add(0, currentFolder.getFolderName());
                }
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
        apiProjectInfoService.deleteByFolder(folderId);
        return this.removeById(folderId);
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        apiDocService.deleteByProject(projectId);
        return this.remove(Wrappers.<ApiFolder>query().eq("project_id", projectId));
    }

    @Override
    public boolean existsApiFolder(ApiFolder folder) {
        List<ApiFolder> existsItems = list(Wrappers.<ApiFolder>query().eq("project_id", folder.getProjectId())
                .eq("parent_id", folder.getParentId())
                .eq("folder_name", folder.getFolderName()));
        return existsItems.stream().anyMatch(item -> !item.getId().equals(folder.getId()));
    }

    @Override
    public Map<Integer, Pair<ApiFolder, ApiFolder>> copyProjectFolders(Integer fromProjectId, Integer toProjectId, Integer id) {
        List<ApiFolder> folders = loadApiFolders(fromProjectId);
        Map<Integer, Pair<ApiFolder, ApiFolder>> results = new HashMap<>();
        Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMap = calcFolderMap(folders);
        Map<String, ApiFolder> pathFolderMap = folderMap.getKey();
        Map<Integer, ApiFolder> oldToNewFolderMap = new HashMap<>();
        pathFolderMap.keySet().stream().sorted(Comparator.comparingInt(String::length)) // 父文件夹总比子文件夹短
                .forEach(key -> {
                    ApiFolder fromFolder = pathFolderMap.get(key);
                    ApiFolder toFolder = SimpleModelUtils.copy(fromFolder, ApiFolder.class);
                    toFolder.setId(null);
                    toFolder.setProjectId(toProjectId);
                    if (fromProjectId.equals(toProjectId)) {
                        toFolder.setFolderName(fromFolder.getFolderName() + ApiDocConstants.COPY_SUFFIX);
                    }
                    if (toFolder.getParentId() != null) {
                        ApiFolder apiFolder = oldToNewFolderMap.get(toFolder.getParentId());
                        if (apiFolder != null) {
                            toFolder.setParentId(apiFolder.getId());
                        }
                    }
                    save(SimpleModelUtils.addAuditInfo(toFolder));
                    oldToNewFolderMap.put(fromFolder.getId(), toFolder);
                    results.put(fromFolder.getId(), Pair.of(fromFolder, toFolder));
                });
        return results;
    }

    @Override
    public boolean updateSorts(ApiDocConfigSortsVo sortsVo, ApiFolder parentFolder) {
        Map<Integer, ApiDocSortVo> docSortMap = sortsVo.getSorts().stream()
                .filter(sort -> sort.getDocId() != null).distinct()
                .collect(Collectors.toMap(ApiDocSortVo::getDocId, Function.identity()));
        Map<Integer, ApiDocSortVo> folderSortMap = sortsVo.getSorts().stream()
                .filter(sort -> sort.getFolderId() != null).distinct()
                .collect(Collectors.toMap(ApiDocSortVo::getFolderId, Function.identity()));
        List<ApiDoc> apiDocs;
        List<ApiFolder> apiFolders;
        if (!CollectionUtils.isEmpty(docSortMap.keySet())) {
            apiDocs = apiDocService.listByIds(docSortMap.keySet());
            apiDocs.forEach(apiDoc -> {
                ApiDocSortVo sortVo = docSortMap.get(apiDoc.getId());
                if (sortVo != null) {
                    apiDoc.setFolderId(parentFolder.getId());
                    apiDoc.setSortId(sortVo.getSortId());
                    apiDocService.saveOrUpdate(apiDoc);
                }
            });
        }
        if (!CollectionUtils.isEmpty(folderSortMap.keySet())) {
            apiFolders = listByIds(folderSortMap.keySet());
            apiFolders.forEach(apiFolder -> {
                ApiDocSortVo sortVo = folderSortMap.get(apiFolder.getId());
                if (sortVo != null) {
                    apiFolder.setSortId(sortVo.getSortId());
                    apiFolder.setParentId(parentFolder.getId());
                    saveOrUpdate(apiFolder);
                }
            });
        }
        return true;
    }
}
