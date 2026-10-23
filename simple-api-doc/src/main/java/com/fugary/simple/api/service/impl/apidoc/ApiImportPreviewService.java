package com.fugary.simple.api.service.impl.apidoc;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.service.impl.apidoc.asset.DocAssetStorageServiceImpl;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.*;
import com.fugary.simple.api.web.vo.imports.*;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** 只读比较当前项目与解析结果；确认仍调用原导入接口，不保存预览会话。 */
@Service
@RequiredArgsConstructor
public class ApiImportPreviewService {
    private final ApiProjectService projects;
    private final ApiFolderService folders;
    private final ApiDocService docs;
    private final ApiProjectInfoService infos;
    private final ApiProjectInfoDetailService details;
    private final ApiProjectAccessService access;
    private final ApiProjectTaskService tasks;

    public SimpleResult<ImportPreviewVo> preview(ApiProjectTaskImportVo options,
            Supplier<SimpleResult<ExportApiProjectVo>> parse) {
        if (options.getTaskId() != null) {
            ApiProjectTask task = tasks.getById(options.getTaskId());
            if (task == null) { return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404); }
            if (!access.canAccessTask(task, ApiGroupAuthority.WRITABLE)) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
            }
            SimpleTaskUtils.fillImportOptions(task, options);
        }
        ApiProject project = options.getProjectId() == null ? null : projects.getById(options.getProjectId());
        if (project == null) { return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_404); }
        if (!access.canAccessProject(project, ApiGroupAuthority.WRITABLE)) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_403);
        }
        ApiFolder mount = options.getToFolder() == null ? folders.getRootFolder(project.getId()) : folders.getById(options.getToFolder());
        if (options.getToFolder() != null && (mount == null || !Objects.equals(mount.getProjectId(), project.getId()))) {
            return SimpleResultUtils.createError(SimpleResultUtils.getErrorMsg("simple.import.preview.folder", new Object[0]));
        }
        SimpleResult<ExportApiProjectVo> parsed = DocAssetStorageServiceImpl.withoutSavingImages(parse);
        if (!parsed.isSuccess()) { return SimpleResultUtils.createError(parsed.getCode(), parsed.getMessage()); }
        ExportApiProjectVo incoming = parsed.getResultData();
        // 任务执行不保存 URL 推导出的文件名，沿用原任务导入的来源配置规则。
        if (options.getTaskId() != null) { incoming.getProjectInfo().setFileName(null); }
        ImportPreviewVo view = new ImportPreviewVo();
        view.setProjectName(project.getProjectName());
        view.setSource(ImportPreviewVo.safeSource(ApiDocConstants.IMPORT_TYPE_URL.equals(options.getImportType()) ? options.getUrl() : options.getFileName()));
        view.setSourceTitle(incoming.getProjectName());
        view.setSourceVersion(incoming.getProjectInfo().getVersion());
        if (mount == null) {
            mount = new ApiFolder();
            mount.setId(-1);
            mount.setProjectId(project.getId());
            mount.setRootFlag(true);
            mount.setFolderCode("root");
            mount.setFolderName("根目录");
            mount.setStatus(ApiDocConstants.STATUS_ENABLED);
            mount.setSortId(0);
            view.record("folder", mount.getFolderName(), "root", "added", null, mount);
        }
        List<ApiFolder> savedFolders = folders.loadApiFolders(project.getId());
        view.setTargetFolder(folders.calcFolderNameMap(savedFolders).getOrDefault(mount.getId(), mount.getFolderName()));
        List<ApiProjectInfo> savedInfos = infos.loadByProjectId(project.getId());
        Integer mountId = mount.getId();
        ExportApiProjectInfoVo info = incoming.getProjectInfo();
        ApiProjectInfo oldInfo = ApiDocParseUtils.prepareImportInfo(info, project.getId(), mountId, savedInfos, true);
        view.record("source", mount.getFolderName(), "", null, oldInfo, SimpleModelUtils.copy(info, ApiProjectInfo.class));
        if (Boolean.TRUE.equals(info.getDefaultFlag())) {
            ApiProject merged = SimpleModelUtils.copy(project, ApiProject.class);
            ApiDocParseUtils.mergeImportProject(merged, incoming);
            view.record("project", project.getProjectName(), "", null, project, merged);
        }
        compareModels(project, info, incoming.getProjectInfoDetails(), view);
        Pair<Map<String, ApiFolder>, Map<Integer, String>> folderMap = folders.calcFolderMap(savedFolders);
        folderMap.getLeft().putIfAbsent("root", mount);
        String mountPath = folderMap.getRight().getOrDefault(mountId, "root");
        List<ApiDoc> savedDocs = docs.loadByProject(project.getId()).stream()
                .filter(doc -> oldInfo != null && Objects.equals(doc.getInfoId(), oldInfo.getId())).collect(Collectors.toList());
        Map<String, ApiDocDetailVo> docMap = details.loadDetailList(savedDocs).stream()
                .collect(Collectors.toMap(ApiDoc::getDocKey, Function.identity(), (first, last) -> last));
        Comparison comparison = new Comparison(view, info, folderMap.getLeft(), docMap, mountPath);
        comparison.compareFolders(incoming.getFolders(), mount);
        comparison.compareDocs(incoming.getDocs(), mount);
        comparison.removeEmptyFolders();
        return SimpleResultUtils.createSimpleResult(view);
    }

    private void compareModels(ApiProject project, ApiProjectInfo info, List<ExportApiProjectInfoDetailVo> incoming, ImportPreviewVo view) {
        Set<String> types = new HashSet<>(ApiDocConstants.PROJECT_SCHEMA_TYPES);
        types.add(ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT);
        Map<String, ApiProjectInfoDetail> saved = info.getId() == null ? Collections.emptyMap()
                : details.loadByProjectAndInfo(project.getId(), info.getId(), types).stream()
                .collect(Collectors.toMap(ApiDocParseUtils::getProjectInfoDetailKey, Function.identity(), (first, last) -> last));
        for (ExportApiProjectInfoDetailVo model : incoming) {
            model.setProjectId(project.getId());
            model.setInfoId(info.getId());
            ApiProjectInfoDetail before = saved.get(ApiDocParseUtils.getProjectInfoDetailKey(model));
            ApiProjectInfoDetail after = ApiDocParseUtils.processProjectInfoDetail(saved, model, SchemaJsonUtils.isV31(info.getSpecVersion())).getLeft();
            boolean snapshot = ApiDocConstants.PROJECT_SCHEMA_TYPE_CONTENT.equals(model.getBodyType());
            view.record(snapshot ? "sourceContent" : "model", StringUtils.defaultIfBlank(model.getSchemaName(), model.getBodyType()),
                    model.getBodyType(), after == null ? "unchanged" : before == null ? "added" : "updated",
                    before, after == null ? before : after);
        }
    }

    @RequiredArgsConstructor
    private static class Comparison {
        private final ImportPreviewVo view;
        private final ApiProjectInfo info;
        private final Map<String, ApiFolder> folderMap;
        private final Map<String, ApiDocDetailVo> docMap;
        private final String mountPath;
        private final List<ExportApiDocVo> importedDocs = new ArrayList<>();
        private final Map<String, ApiFolder> addedFolders = new HashMap<>();
        private int nextFolderId = -2;

        void compareFolders(List<ExportApiFolderVo> incoming, ApiFolder parent) {
            if (incoming == null) { return; }
            for (ExportApiFolderVo folder : incoming) {
                String path = ApiDocParseUtils.calcImportFolderPath(mountPath, folder);
                ApiFolder before = folderMap.get(path);
                ApiDocParseUtils.prepareImportFolder(folder, before, parent);
                if (!path.equals(mountPath)) {
                    if (before == null) { folder.setId(nextFolderId--); }
                    view.record("folder", folder.getFolderName(), path, null, before, SimpleModelUtils.copy(folder, ApiFolder.class));
                    if (before == null) { addedFolders.put(path, folder); }
                    folderMap.put(path, folder);
                }
                compareDocs(folder.getDocs(), folder);
                compareFolders(folder.getFolders(), folder);
            }
        }

        void compareDocs(List<ExportApiDocVo> incoming, ApiFolder folder) {
            if (incoming == null) { return; }
            for (ExportApiDocVo doc : incoming) {
                ApiDocDetailVo before = docMap.get(doc.getDocKey());
                ApiDocParseUtils.prepareImportDoc(doc, before, info, folder.getId());
                boolean locked = before != null && Boolean.TRUE.equals(doc.getLocked());
                boolean schemaChanged = !locked && ApiDocParseUtils.processExistsSchemas(doc, before);
                String status = locked ? "locked" : before == null ? "added"
                        : schemaChanged || !ApiDocParseUtils.isSameApiDoc(doc, before) ? "updated" : "unchanged";
                view.record(doc.getDocType(), doc.getDocName(), StringUtils.defaultIfBlank(doc.getUrl(), doc.getDocKey()),
                        status, before, doc).setMethod(doc.getMethod());
                importedDocs.add(doc);
            }
        }

        void removeEmptyFolders() {
            Map<Integer, List<ApiFolder>> children = ApiDocParseUtils.calcChildFoldersMap(folderMap.values());
            view.getChanges().removeIf(change -> "folder".equals(change.getType()) && addedFolders.containsKey(change.getPath())
                    && ApiDocParseUtils.calcFolderDocCount(children, addedFolders.get(change.getPath()), importedDocs) == 0);
        }
    }
}
