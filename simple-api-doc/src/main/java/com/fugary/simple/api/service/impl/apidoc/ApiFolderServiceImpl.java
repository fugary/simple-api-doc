package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.mapper.api.ApiFolderMapper;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import com.fugary.simple.api.web.vo.imports.ImportStatisticsVo;
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
    private ApiProjectInfoDetailService apiDocSchemaService;

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
        return saveApiFolders(project, projectInfo, mountFolder, apiFolders, extraDocs, null);
    }

    @Override
    public int saveApiFolders(ApiProject project, ApiProjectInfo projectInfo, ApiFolder mountFolder, List<ExportApiFolderVo> apiFolders, List<ExportApiDocVo> extraDocs, ImportStatisticsVo stats) {
        mountFolder = getOrCreateMountFolder(project, mountFolder);
        Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair = calcFolderMap(loadApiFolders(project.getId()));
        Map<Integer, String> folderPathMap = folderMapPair.getValue();
        Map<String, Pair<String, ApiDoc>> existsDocMap = calcDocMap(apiDocService.loadByProject(project.getId()), folderPathMap);
        ApiFolder finalMountFolder = mountFolder; // 挂载目录
        List<ApiFolder> newFolders = new ArrayList<>();
        List<ApiDoc> apiDocs = new ArrayList<>();
        apiFolders.forEach(folder -> saveApiFolderVo(projectInfo, finalMountFolder, finalMountFolder, folder, folderMapPair, existsDocMap, newFolders, apiDocs, stats));
        this.saveApiDocs(projectInfo, mountFolder, null, extraDocs, existsDocMap, apiDocs, stats);
        removeFoldersWithoutDocs(folderMapPair, newFolders, apiDocs);
        return apiFolders.size();
    }

    protected void removeFoldersWithoutDocs(Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair, List<ApiFolder> newFolders, List<ApiDoc> apiDocs){
        Map<Integer, List<ApiFolder>> childFoldersMap = ApiDocParseUtils.calcChildFoldersMap(folderMapPair.getLeft().values());
        Set<Integer> foldersToDelete = new HashSet<>();
        newFolders.forEach(folder -> {
            int docCount = ApiDocParseUtils.calcFolderDocCount(childFoldersMap, folder, apiDocs);
            if (docCount == 0) {
                foldersToDelete.add(folder.getId());
            }
        });
        removeByIds(foldersToDelete);
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
        saveApiFolderVo(projectInfo, rootFolder, parentFolder, folder, folderMapPair, existsDocMap, newFolders, apiDocs, null);
    }

    protected void saveApiFolderVo(ApiProjectInfo projectInfo, ApiFolder rootFolder, ApiFolder parentFolder, ExportApiFolderVo folder,
                                   Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMapPair,
                                   Map<String, Pair<String, ApiDoc>> existsDocMap,
                                   List<ApiFolder> newFolders, List<ApiDoc> apiDocs,
                                   ImportStatisticsVo stats) {
        Map<String, ApiFolder> pathFolderMap = folderMapPair.getKey();
        Map<Integer, String> folderPathMap = folderMapPair.getValue();
        String mountPath = folderPathMap.get(rootFolder.getId());
        String folderPath = ApiDocParseUtils.calcImportFolderPath(mountPath, folder);
        ApiFolder existsFolder = pathFolderMap.get(folderPath);
        ApiDocParseUtils.prepareImportFolder(folder, existsFolder, parentFolder);
        // 无 Tag 的虚拟目录只借用目标目录，不修改目标目录或它的路径索引。
        if (!folderPath.equals(mountPath)) {
            if (existsFolder == null) {
                save(SimpleModelUtils.addAuditInfo(folder));
                newFolders.add(folder);
                if (stats != null) {
                    stats.addFolderAdded();
                }
                existsFolder = folder;
            } else {
                updateById(SimpleModelUtils.addAuditInfo(folder)); // 更新
            }
            pathFolderMap.put(folderPath, folder);
            folderPathMap.put(folder.getId(), folderPath);
        }
        saveApiDocs(projectInfo, parentFolder, folder, folder.getDocs(), existsDocMap, apiDocs, stats);
        if (!CollectionUtils.isEmpty(folder.getFolders())) { // 子目录
            for (ExportApiFolderVo subFolder : folder.getFolders()) {
                saveApiFolderVo(projectInfo, rootFolder, existsFolder, subFolder, folderMapPair, existsDocMap, newFolders, apiDocs, stats);
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
        saveApiDocs(projectInfo, parentFolder, folder, docs, existsDocMap, apiDocs, null);
    }

    /**
     * 保存doc信息（带统计）
     *
     * @param projectInfo 项目基本信息
     * @param parentFolder   挂载目录
     * @param folder        父级目录
     * @param docs          文档列表
     * @param existsDocMap  已保存文档map
     * @param apiDocs  保存的apiDocs信息
     * @param stats  变更统计
     */
    protected void saveApiDocs(ApiProjectInfo projectInfo, ApiFolder parentFolder,
                               ExportApiFolderVo folder, List<ExportApiDocVo> docs,
                               Map<String, Pair<String, ApiDoc>> existsDocMap, List<ApiDoc> apiDocs,
                               ImportStatisticsVo stats) {
        if (!CollectionUtils.isEmpty(docs)) {
            Integer folderId = parentFolder.getId();
            if (folder != null) {
                folderId = folder.getId();
            }
            Map<Integer, ApiDocDetailVo> existsDocDetailMap = loadExistsDocDetailMap(projectInfo, docs, existsDocMap);
            // 保存folder docs
            for (ExportApiDocVo apiDocVo : docs) {
                Pair<String, ApiDoc> existsDocPair = existsDocMap.get(calcDocKey(projectInfo.getId(), apiDocVo));
                ApiDoc existsDoc = existsDocPair == null ? null : existsDocPair.getValue();
                ApiDocParseUtils.prepareImportDoc(apiDocVo, existsDoc, projectInfo, folderId);
                boolean locked = existsDoc != null && Boolean.TRUE.equals(apiDocVo.getLocked());
                ApiDocDetailVo existsDocDetail = locked ? null : existsDocDetailMap.get(apiDocVo.getId());
                if (!locked) {
                    boolean schemaChanged = ApiDocParseUtils.processExistsSchemas(apiDocVo, existsDocDetail);
                    boolean isNew = (existsDoc == null);
                    boolean isSame = existsDoc != null && ApiDocParseUtils.isSameApiDoc(apiDocVo, existsDoc);
                    boolean isChanged = !isSame || schemaChanged;
                    apiDocService.saveApiDoc(SimpleModelUtils.addAuditInfo(apiDocVo), existsDoc, schemaChanged);
                    saveApiDocSchemas(apiDocVo);
                    if (stats != null) {
                        if (isNew) {
                            stats.addDocAdded();
                        } else if (isChanged) {
                            stats.addDocUpdated();
                        } else {
                            stats.addDocUnchanged();
                        }
                    }
                } else if (stats != null) {
                    stats.addDocLocked();
                }
                apiDocs.add(apiDocVo);
            }
        }
    }

    protected Map<Integer, ApiDocDetailVo> loadExistsDocDetailMap(ApiProjectInfo projectInfo, List<ExportApiDocVo> docs,
                                                                  Map<String, Pair<String, ApiDoc>> existsDocMap) {
        List<ApiDoc> existsDocs = docs.stream()
                .map(apiDocVo -> existsDocMap.get(calcDocKey(projectInfo.getId(), apiDocVo)))
                .filter(Objects::nonNull)
                .map(Pair::getValue)
                .filter(Objects::nonNull)
                .filter(doc -> !Boolean.TRUE.equals(doc.getLocked()))
                .collect(Collectors.toMap(ApiDoc::getId, Function.identity(), (existing, replacement) -> existing, LinkedHashMap::new))
                .values().stream().collect(Collectors.toList());
        if (existsDocs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, ApiDocDetailVo> detailMap = apiDocSchemaService.loadDetailList(existsDocs).stream()
                .collect(Collectors.toMap(ApiDocDetailVo::getId, Function.identity(), (existing, replacement) -> existing));
        apiDocSchemaService.deleteByDocs(existsDocs.stream().map(ApiDoc::getId).collect(Collectors.toList()));
        return detailMap;
    }

    protected void saveApiDocSchemas(ExportApiDocVo apiDocVo) {
        List<ApiProjectInfoDetail> docSchemas = new ArrayList<>();
        if (apiDocVo.getSecurityRequirements() != null) {
            docSchemas.add(apiDocVo.getSecurityRequirements());
        }
        if (apiDocVo.getParametersSchema() != null) {
            docSchemas.add(apiDocVo.getParametersSchema());
        }
        if (!CollectionUtils.isEmpty(apiDocVo.getRequestsSchemas())) {
            docSchemas.addAll(apiDocVo.getRequestsSchemas());
        }
        if (!CollectionUtils.isEmpty(apiDocVo.getResponsesSchemas())) {
            docSchemas.addAll(apiDocVo.getResponsesSchemas());
        }
        if (!docSchemas.isEmpty()) {
            apiDocSchemaService.saveBatch(docSchemas.stream().map(docSchema -> {
                setApiDocInfo(docSchema, apiDocVo);
                return SimpleModelUtils.addAuditInfo(docSchema);
            }).collect(Collectors.toList()));
        }
    }

    protected void setApiDocInfo(ApiProjectInfoDetail infoDetail, ApiDoc apiDoc) {
        infoDetail.setDocId(apiDoc.getId());
        infoDetail.setProjectId(apiDoc.getProjectId());
        infoDetail.setInfoId(apiDoc.getInfoId());
        infoDetail.setVersion(1);
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
        rootFolder.setFolderCode("root");
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
            String folderPath = calcFolderPath(apiFolder, folderMap, f -> StringUtils.defaultIfBlank(f.getFolderCode(), f.getFolderName()));
            pathFolderMap.put(folderPath, apiFolder);
            folderPathMap.put(apiFolder.getId(), folderPath);
        }
        return Pair.of(pathFolderMap, folderPathMap);
    }

    @Override
    public Map<Integer, String> calcFolderNameMap(List<ApiFolder> apiFolders) {
        Map<Integer, ApiFolder> folderMap = apiFolders.stream().collect(Collectors.toMap(ApiFolder::getId, Function.identity()));
        Map<Integer, String> folderNameMap = new HashMap<>();
        for (ApiFolder apiFolder : apiFolders) {
            String folderNamePath = calcFolderPath(apiFolder, folderMap, f -> StringUtils.defaultIfBlank(f.getFolderName(), f.getFolderCode()));
            folderNameMap.put(apiFolder.getId(), folderNamePath);
        }
        return folderNameMap;
    }

    protected String calcFolderPath(ApiFolder apiFolder, Map<Integer, ApiFolder> folderMap, Function<ApiFolder, String> nameExtractor) {
        List<String> paths = new ArrayList<>();
        paths.add(nameExtractor.apply(apiFolder));
        ApiFolder currentFolder = apiFolder;
        Set<Integer> visited = new HashSet<>();
        visited.add(apiFolder.getId());
        while (currentFolder != null && currentFolder.getParentId() != null && visited.add(currentFolder.getParentId())) {
            currentFolder = folderMap.get(currentFolder.getParentId());
            if (currentFolder != null) {
                paths.add(0, nameExtractor.apply(currentFolder));
            }
        }
        return String.join("/", paths);
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
                    toFolder.setFolderCode(fromFolder.getFolderCode());
                    if (fromProjectId.equals(toProjectId)) {
                        toFolder.setFolderCode(fromFolder.getFolderCode() + ApiDocConstants.COPY_SUFFIX);
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
            if (apiDocs.stream().anyMatch(apiDoc -> !Objects.equals(apiDoc.getProjectId(), parentFolder.getProjectId()))) {
                return false;
            }
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
            if (apiFolders.stream().anyMatch(apiFolder -> !Objects.equals(apiFolder.getProjectId(), parentFolder.getProjectId()))) {
                return false;
            }
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
