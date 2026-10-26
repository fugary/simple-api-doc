package com.fugary.simple.api.imports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.*;
import com.fugary.simple.api.service.apidoc.*;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.service.apidoc.content.DocContentProvider;
import com.fugary.simple.api.tasks.ProjectAutoImportInvoker;
import com.fugary.simple.api.utils.task.SimpleTaskUtils;
import com.fugary.simple.api.web.controllers.admin.ApiProjectImportController;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import com.fugary.simple.api.event.log.OperationLogEvent;
import com.fugary.simple.api.service.impl.apidoc.ApiImportPreviewService;
import com.fugary.simple.api.service.impl.apidoc.ImportSourceCache;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.springframework.test.util.ReflectionTestUtils;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.*;
import com.fugary.simple.api.web.vo.imports.*;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:import-preview;DB_CLOSE_DELAY=-1",
        "dbs.h2.data-dir=target/import-preview-test",
        "logging.file.name=target/import-preview-test/test.log"
})
@Import(ApiImportPreviewIntegrationTest.LogEvents.class)
class ApiImportPreviewIntegrationTest {
    @Autowired ApiImportPreviewService previews;
    @Autowired ApiProjectService projects;
    @Autowired ApiFolderService folders;
    @Autowired ApiDocService docs;
    @Autowired ApiProjectInfoService infos;
    @Autowired ApiProjectInfoDetailService details;
    @Autowired DocAssetStorageService assets;
    @MockBean ApiProjectAccessService access;
    @MockBean(name = "urlDocContentProviderImpl") DocContentProvider<UrlWithAuthVo> urlContent;
    @Autowired ProjectAutoImportInvoker taskInvoker;
    @Autowired ApiProjectImportController imports;
    @Autowired LogEvents events;
    @Autowired ApiProjectTaskService tasks;
    @Autowired ImportSourceCache sourceCache;

    @TestConfiguration
    static class LogEvents {
        final List<ApiLog> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
        @EventListener void record(OperationLogEvent event) { logs.add(event.getApiLog()); }
    }
    ApiProject project;
    ApiProjectTaskImportVo options;

    @BeforeEach void setup() {
        login("preview-user");
        events.logs.clear();
        when(access.canAccessProject(any(ApiProject.class), any())).thenReturn(true);
        project = new ApiProject();
        project.setProjectCode(UUID.randomUUID().toString());
        project.setProjectName("Existing project");
        project.setUserName("preview-user");
        projects.save(project);
        folders.createRootFolder(project);
        options = new ApiProjectTaskImportVo();
        options.setProjectId(project.getId());
        options.setImportType("file");
        options.setSourceType("openapi");
        options.setFileName("api.yaml");
    }

    @AfterEach void cleanup() {
        sourceCache.clear();
        login("preview-user");
        projects.deleteApiProject(project.getId());
        RequestContextHolder.resetRequestAttributes();
    }

    private void login(String name) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiUserVo user = new ApiUserVo();
        user.setUserName(name);
        request.setAttribute(ApiDocConstants.API_USER_KEY, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ExportApiProjectVo payload(String description) {
        ExportApiProjectVo export = new ExportApiProjectVo();
        export.setProjectName("Source project");
        ExportApiProjectInfoVo info = new ExportApiProjectInfoVo();
        info.setVersion("1.0");
        info.setSpecVersion("V30");
        info.setImportType("file");
        info.setFileName("api.yaml");
        export.setProjectInfo(info);
        ExportApiDocVo doc = new ExportApiDocVo();
        doc.setDocType(ApiDocConstants.DOC_TYPE_API);
        doc.setDocKey("GET:/pets");
        doc.setDocName("List pets");
        doc.setSummary("List pets");
        doc.setUrl("/pets");
        doc.setMethod("GET");
        doc.setDescription(description);
        doc.setStatus(ApiDocConstants.STATUS_ENABLED);
        export.getDocs().add(doc);
        return export;
    }

    private ImportPreviewVo preview(ExportApiProjectVo export) {
        SimpleResult<ImportPreviewVo> result = previews.preview(options, () -> SimpleResultUtils.createSimpleResult(export));
        assertTrue(result.isSuccess(), result.getMessage());
        return result.getResultData();
    }

    private String database() {
        return JsonUtils.toJson(Arrays.asList(projects.getById(project.getId()), folders.loadApiFolders(project.getId()),
                docs.loadByProject(project.getId()), infos.loadByProjectId(project.getId()), details.loadByProject(project.getId(), Set.of())));
    }

    private ImportPreviewVo.Change docChange(ImportPreviewVo view) {
        return view.getChanges().stream().filter(c -> ApiDocConstants.DOC_TYPE_API.equals(c.getType())).findFirst().orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "md"})
    void copiedDocGetsNewCreationTimeWithoutChangingOriginal(String docType) {
        ExportApiProjectVo source = payload("Original content");
        source.getDocs().get(0).setDocType(docType);
        projects.importUpdateProject(project, source, options);
        ApiDoc original = docs.loadByProject(project.getId()).get(0);
        Date oldDate = new Date(1600000000000L);
        original.setCreateDate(oldDate);
        original.setModifyDate(oldDate);
        docs.updateById(original);

        long beforeCopy = System.currentTimeMillis();
        SimpleResult<ApiDoc> result = docs.copyApiDoc(original, folders.getById(original.getFolderId()));
        assertTrue(result.isSuccess(), result.getMessage());
        ApiDoc copy = docs.getById(result.getResultData().getId());
        assertNotEquals(original.getId(), copy.getId());
        assertTrue(copy.getCreateDate().getTime() >= beforeCopy);
        assertNull(copy.getModifyDate());
        assertEquals(original.getDescription(), copy.getDescription());
        assertEquals(oldDate, docs.getById(original.getId()).getCreateDate());
        assertEquals(oldDate, docs.getById(original.getId()).getModifyDate());
    }

    @Test void previewOnlyReadsAndReturnsDiffWithoutServerSession() {
        String before = database();
        ImportPreviewVo view = preview(payload("Preview"));
        assertEquals(before, database());
        assertEquals("added", docChange(view).getStatus());
        assertTrue(docChange(view).getModified().contains("Preview"));
        assertFalse(JsonUtils.toJson(view).contains("previewId"));
        assertFalse(JsonUtils.toJson(view).contains("expiresAt"));
        assertTrue(events.logs.isEmpty());
        assertEquals(before, database());
    }

    @Test void confirmedImportReusesPreviewSourceAndReleasesCache() {
        options.setImportType(ApiDocConstants.IMPORT_TYPE_URL);
        options.setSourceType("markdown");
        options.setUrl("https://example.test/guide.md");
        when(urlContent.getContent(any(UrlWithAuthVo.class))).thenReturn(
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nPreview version")),
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nLatest version")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertTrue(imports.loadImportPreview(options, request).isSuccess());
        assertTrue(docs.loadByProject(project.getId()).isEmpty());
        assertTrue(imports.importExistsProject(options, request).isSuccess());
        assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("Preview version"));
        assertFalse(cachedSource(options).isSuccess());
        org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(1)).getContent(any(UrlWithAuthVo.class));
    }

    private SimpleResult<DocSourceData> cachedSource(ApiProjectTaskImportVo options) {
        return sourceCache.getContent(SimpleModelUtils.copy(options, ApiProjectTaskImportVo.class), false,
                () -> SimpleResultUtils.createError("Cache miss"));
    }

    @Test void expiredCacheFallsBackToOriginalDownloadAndImport() {
        options.setImportType("url");
        options.setSourceType("markdown");
        options.setUrl("https://example.test/guide.md");
        when(urlContent.getContent(any())).thenReturn(
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nPreview")),
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nLatest")));
        org.springframework.test.util.ReflectionTestUtils.setField(sourceCache, "ttlMillis", -1L);
        try {
            assertTrue(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
            sourceCache.cleanExpired();
            assertTrue(imports.importExistsProject(options, new MockHttpServletRequest()).isSuccess());
            assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("Latest"));
            org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(2)).getContent(any());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(sourceCache, "ttlMillis", 600000L);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fileName", "projectName", "iconUrl", "groupCode", "authContent"})
    void changedImportParametersFallBackToFreshSource(String field) {
        options.setImportType("url");
        options.setSourceType("markdown");
        options.setUrl("https://example.test/guide.md");
        ApiProjectTaskImportVo confirmation = SimpleModelUtils.copy(options, ApiProjectTaskImportVo.class);
        when(urlContent.getContent(any())).thenReturn(
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nPreview")),
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nLatest")));
        ImportPreviewVo preview = imports.loadImportPreview(options, new MockHttpServletRequest()).getResultData();
        confirmation.setPreviewKey(preview.getPreviewKey());
        ReflectionTestUtils.setField(confirmation, field, "changed");
        assertTrue(imports.importExistsProject(confirmation, new MockHttpServletRequest()).isSuccess());
        assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("Latest"));
        assertTrue(cachedSource(options).isSuccess());
        org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(2)).getContent(any());
    }

    @Test void separateConfirmationRequestReusesAutomaticallyDetectedSource() {
        options.setImportType("url");
        options.setSourceType(null);
        options.setFileName(null);
        options.setUrl("https://example.test/guide.md");
        ApiProjectTaskImportVo confirmation = SimpleModelUtils.copy(options, ApiProjectTaskImportVo.class);
        when(urlContent.getContent(any())).thenReturn(SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide")));
        ImportPreviewVo preview = imports.loadImportPreview(options, new MockHttpServletRequest()).getResultData();
        confirmation.setPreviewKey(preview.getPreviewKey());
        assertTrue(imports.importExistsProject(confirmation, new MockHttpServletRequest()).isSuccess());
        org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(1)).getContent(any());
    }

    @Test void missingCachePreservesOriginalDownloadFailure() {
        options.setImportType("url");
        options.setUrl("https://example.test/guide.md");
        options.setPreviewKey("missing");
        when(urlContent.getContent(any())).thenReturn(SimpleResultUtils.createError(2009, "Download failed"));
        SimpleResult<ApiProject> result = imports.importExistsProject(options, new MockHttpServletRequest());
        assertEquals(2009, result.getCode());
        assertEquals("Download failed", result.getMessage());
        assertTrue(docs.loadByProject(project.getId()).isEmpty());
    }

    @Test void unsuccessfulPreviewReleasesDownloadedSource() {
        options.setImportType("url");
        options.setUrl("https://example.test/guide.md");
        when(urlContent.getContent(any())).thenReturn(SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Not OpenAPI")));
        assertFalse(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
        assertNotNull(options.getPreviewKey());
        assertFalse(cachedSource(options).isSuccess());
    }

    @Test void failedImageRestoreFallsBackAndReleasesCache() {
        DocAssetStorageService failingAssets = org.mockito.Mockito.mock(DocAssetStorageService.class);
        ImportSourceCache cache = new ImportSourceCache(failingAssets);
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String code = "cache-test-" + UUID.randomUUID();
        try {
            SimpleResult<DocSourceData> preview = cache.getContent(options, true, () -> {
                String url = assets.saveImage(image, "picture.png", code);
                return SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\n![](" + url + ")"));
            });
            assertTrue(preview.isSuccess());
            assertFalse(Files.exists(Path.of(assets.getBaseUploadPath(), "docs", code)));
            AtomicInteger downloads = new AtomicInteger();
            String key = options.getPreviewKey();
            for (int i = 0; i < 2; i++) {
                options.setPreviewKey(key);
                SimpleResult<DocSourceData> restored = cache.getContent(options, false, () -> {
                    downloads.incrementAndGet();
                    return SimpleResultUtils.createSimpleResult(DocSourceData.ofText("Fresh source"));
                });
                assertEquals("Fresh source", restored.getResultData().getTextContent());
            }
            assertEquals(2, downloads.get());
            org.mockito.Mockito.verify(failingAssets, org.mockito.Mockito.times(1)).saveImage(any(), any(), any());
        } finally {
            cache.clear();
        }
    }

    @Test void cacheIsBoundToUserSourceAndTarget() {
        options.setImportType("url");
        options.setSourceType("markdown");
        options.setUrl("https://example.test/guide.md");
        when(urlContent.getContent(any())).thenReturn(SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide")));
        assertTrue(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
        login("another-user");
        assertFalse(cachedSource(options).isSuccess());
        login("preview-user");
        options.setAuthContent("changed-token");
        assertFalse(cachedSource(options).isSuccess());
        options.setAuthContent(null);
        options.setToFolder(Integer.MAX_VALUE);
        assertFalse(cachedSource(options).isSuccess());
        options.setToFolder(null);
        assertTrue(cachedSource(options).isSuccess());
        when(access.canAccessProject(any(ApiProject.class), any())).thenReturn(false);
        assertFalse(imports.importExistsProject(options, new MockHttpServletRequest()).isSuccess());
        assertTrue(docs.loadByProject(project.getId()).isEmpty());
    }

    @Test void cacheCapacityEvictsOldestAndSkipsOversizedSources() {
        options.setImportType("url");
        options.setSourceType("markdown");
        options.setUrl("https://example.test/guide.md");
        org.springframework.test.util.ReflectionTestUtils.setField(sourceCache, "maxTotalBytes", 20L);
        org.springframework.test.util.ReflectionTestUtils.setField(sourceCache, "maxEntryBytes", 20L);
        try {
            when(urlContent.getContent(any())).thenReturn(SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide")));
            assertTrue(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
            String firstKey = options.getPreviewKey();
            assertTrue(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
            String secondKey = options.getPreviewKey();
            options.setPreviewKey(firstKey);
            assertFalse(cachedSource(options).isSuccess());
            options.setPreviewKey(secondKey);
            assertTrue(cachedSource(options).isSuccess());
            when(urlContent.getContent(any())).thenReturn(SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Too large for cache")));
            assertTrue(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
            assertNull(options.getPreviewKey());
            assertTrue(imports.importExistsProject(options, new MockHttpServletRequest()).isSuccess());
            org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(4)).getContent(any());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(sourceCache, "maxTotalBytes", 268435456L);
            org.springframework.test.util.ReflectionTestUtils.setField(sourceCache, "maxEntryBytes", 67108864L);
        }
    }

    @Test void gitPreviewRetainsImagesWithoutSavingOrCloningAgain() throws Exception {
        options.setImportType("url");
        options.setSourceType("markdown");
        options.setUrl("https://example.test/repo.git");
        String imageProject = "cache-test-" + UUID.randomUUID();
        java.util.concurrent.atomic.AtomicReference<Path> clonePath = new java.util.concurrent.atomic.AtomicReference<>();
        byte[] bytes = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        com.fugary.simple.api.service.impl.apidoc.git.GitDocContentProviderImpl git =
                new com.fugary.simple.api.service.impl.apidoc.git.GitDocContentProviderImpl() {
                    @Override protected void doCloneRepository(String url, String branch, java.io.File dir,
                            org.eclipse.jgit.transport.CredentialsProvider credentials) throws Exception {
                        clonePath.set(dir.toPath());
                        Files.write(dir.toPath().resolve("picture.png"), bytes);
                        Files.writeString(dir.toPath().resolve("guide.md"), "# Guide\n![picture](picture.png)");
                    }
                };
        org.springframework.test.util.ReflectionTestUtils.setField(git, "docAssetStorageService", assets);
        com.fugary.simple.api.web.vo.git.GitRepoInfo repo = com.fugary.simple.api.web.vo.git.GitRepoInfo.builder()
                .cloneUrl(options.getUrl()).repo(imageProject).branch("main").build();
        when(urlContent.getContent(any())).thenAnswer(call -> git.getContent(repo, options));
        Path imageDir = Path.of(assets.getBaseUploadPath(), "docs", imageProject);
        try {
            assertTrue(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
            assertFalse(Files.exists(imageDir));
            assertFalse(Files.exists(clonePath.get()));
            assertTrue(imports.importExistsProject(options, new MockHttpServletRequest()).isSuccess());
            String name = org.apache.commons.codec.digest.DigestUtils.md5Hex(bytes) + ".png";
            assertArrayEquals(bytes, Files.readAllBytes(imageDir.resolve(name)));
            assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("/upload/docs/" + imageProject + "/" + name));
            org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(1)).getContent(any());
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(imageDir.toFile());
        }
    }

    @Test void filePreviewAndOriginalImportReuseTheSameUpload() {
        options.setSourceType("markdown");
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.addFile(new MockMultipartFile("files", "guide.md", "text/markdown",
                "# Guide\nUploaded content".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(imports.loadImportPreview(options, request).isSuccess());
        assertTrue(docs.loadByProject(project.getId()).isEmpty());
        assertTrue(imports.importExistsProject(options, request).isSuccess());
        assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("Uploaded content"));
    }

    @Test void renamedAndLockedDocumentsMatchNormalImport() {
        projects.importUpdateProject(projects.getById(project.getId()), payload("Old"), options);
        ApiDoc doc = docs.loadByProject(project.getId()).get(0);
        doc.setDocName("Custom name");
        doc.setSortId(700);
        docs.updateById(doc);
        ImportPreviewVo.Change change = docChange(preview(payload("New")));
        assertEquals("Custom name", change.getName());
        assertEquals(List.of("description"), change.getChangedFields());
        projects.importUpdateProject(projects.getById(project.getId()), payload("New"), options);
        doc = docs.getById(doc.getId());
        assertEquals("Custom name", doc.getDocName());
        assertEquals(700, doc.getSortId());
        assertEquals("New", doc.getDescription());
        doc.setLocked(true);
        docs.updateById(doc);
        assertEquals("locked", docChange(preview(payload("Locked"))).getStatus());
        projects.importUpdateProject(projects.getById(project.getId()), payload("Locked"), options);
        assertEquals("New", docs.getById(doc.getId()).getDescription());
    }

    @Test void previewPermissionFailureDoesNotParse() {
        when(access.canAccessProject(any(ApiProject.class), any())).thenReturn(false);
        AtomicInteger calls = new AtomicInteger();
        assertFalse(previews.preview(options, () -> {
            calls.incrementAndGet();
            return SimpleResultUtils.createSimpleResult(payload("Denied"));
        }).isSuccess());
        assertEquals(0, calls.get());
    }

    @Test void rejectsForeignFolderBeforeParsing() {
        options.setToFolder(Integer.MAX_VALUE);
        AtomicInteger calls = new AtomicInteger();
        assertFalse(previews.preview(options, () -> {
            calls.incrementAndGet();
            return SimpleResultUtils.createSimpleResult(payload("Never parsed"));
        }).isSuccess());
        assertEquals(0, calls.get());
    }


    @ParameterizedTest
    @ValueSource(strings = {"markdown", "openapi"})
    void base64ImagePreviewMatchesActualImport(String sourceType) throws Exception {
        options.setSourceType(sourceType);
        boolean markdown = "markdown".equals(sourceType);
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String imageMarkdown = embeddedImage(image);
        String content = markdown ? "# Guide\n" + imageMarkdown
                : "openapi: 3.0.1\ninfo:\n  title: Images\n  version: 1.0.0\n  description: 'Image preview: " + imageMarkdown
                + "'\npaths:\n  /images:\n    get:\n      description: '" + imageMarkdown
                + "'\n      responses:\n        '200':\n          description: OK\n";
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.addFile(new MockMultipartFile("files", markdown ? "guide.md" : "images.yaml", "text/plain",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String url = imageUrl(project, image);
        Path path = imagePath(url);
        String before = database();
        SimpleResult<ImportPreviewVo> result = imports.loadImportPreview(options, request);
        assertTrue(result.isSuccess(), result.getMessage());
        ImportPreviewVo.Change change = result.getResultData().getChanges().stream()
                .filter(c -> (markdown ? "md" : "api").equals(c.getType())).findFirst().orElseThrow();
        String previewContent = JsonUtils.fromJson(change.getModified(), com.fasterxml.jackson.databind.JsonNode.class)
                .get(markdown ? "docContent" : "description").asText();
        assertTrue(previewContent.contains(url), previewContent);
        assertEquals(before, database());
        assertFalse(Files.exists(path));
        assertEquals(JsonUtils.toJson(result.getResultData()),
                JsonUtils.toJson(imports.loadImportPreview(options, request).getResultData()));
        try {
            assertTrue(imports.importExistsProject(options, request).isSuccess());
            List<ApiDoc> savedDocs = docs.loadByProject(project.getId());
            ApiDoc saved = savedDocs.stream().filter(doc -> (markdown ? "md" : "api").equals(doc.getDocType()))
                    .findFirst().orElseThrow();
            assertEquals(previewContent, markdown ? saved.getDocContent() : saved.getDescription());
            if (!markdown) {
                ApiDoc overview = savedDocs.stream().filter(doc -> "md".equals(doc.getDocType())).findFirst().orElseThrow();
                assertEquals("Image preview: ![image](" + url + ")", overview.getDocContent());
            }
            assertArrayEquals(image, Files.readAllBytes(path));
            SimpleResult<ImportPreviewVo> repeated = imports.loadImportPreview(options, request);
            assertTrue(repeated.isSuccess(), repeated.getMessage());
            assertFalse(repeated.getResultData().isChanged(), JsonUtils.toJson(repeated.getResultData()));
            SimpleResult<ApiProject> imported = imports.importExistsProject(options, request);
            assertTrue(imported.isSuccess(), imported.getMessage());
            ImportStatisticsVo statistics = (ImportStatisticsVo) imported.getAddons().get("statistics");
            assertEquals(0, statistics.getDocUpdated());
            assertEquals(markdown ? 1 : 2, statistics.getDocUnchanged());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private String embeddedImage(byte[] image) {
        return "![image](data:image/png;base64," + Base64.getEncoder().encodeToString(image) + ")";
    }

    private String imageUrl(ApiProject target, byte[] image) {
        return "/upload/docs/" + target.getProjectCode() + "/"
                + org.apache.commons.codec.digest.DigestUtils.md5Hex(image) + ".png";
    }

    private Path imagePath(String url) {
        return Paths.get(assets.getBaseUploadPath(), url.substring("/upload/".length()));
    }

    @Test void realOpenApiParsingAndModelsCanBePreviewedWithoutWrites() {
        String yaml = "openapi: 3.0.1\ninfo:\n  title: Actual Source\n  version: 1.0.0\npaths:\n  /users:\n    get:\n      summary: Users\n      responses:\n        '200':\n          description: OK\ncomponents:\n  schemas:\n    User:\n      type: object\n      properties:\n        name:\n          type: string\n";
        String before = database();
        SimpleResult<ImportPreviewVo> result = previews.preview(options, () -> projects.processImportProject(DocSourceData.ofText(yaml), options));
        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals("Actual Source", result.getResultData().getSourceTitle());
        assertEquals(before, database());
        assertTrue(result.getResultData().getChanges().stream().anyMatch(c -> "model".equals(c.getType())));
        assertTrue(projects.importUpdateProject(projects.getById(project.getId()),
                projects.processImportProject(DocSourceData.ofText(yaml), options).getResultData(), options).isSuccess());
        assertEquals(1, docs.loadByProject(project.getId()).size());
        SimpleResult<ImportPreviewVo> repeated = previews.preview(options, () -> projects.processImportProject(DocSourceData.ofText(yaml), options));
        assertTrue(repeated.isSuccess());
        assertTrue(repeated.getResultData().getChanges().stream()
                .filter(c -> "api".equals(c.getType()) || "model".equals(c.getType()))
                .allMatch(c -> "unchanged".equals(c.getStatus())), JsonUtils.toJson(repeated.getResultData()));
    }


    @Test void identicalImportHasNoChanges() {
        projects.importUpdateProject(projects.getById(project.getId()), payload("Same"), options);
        ImportPreviewVo view = preview(payload("Same"));
        assertEquals("unchanged", docChange(view).getStatus());
        assertFalse(view.isChanged(), JsonUtils.toJson(view.getChanges()));
    }


    @Test void removedResponseSchemaIsReportedAsUpdated() {
        ExportApiProjectVo export = payload("Same");
        ExportApiDocSchemaVo response = new ExportApiDocSchemaVo();
        response.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE);
        response.setStatusCode(200);
        response.setContentType("application/json");
        response.setSchemaContent("{\"type\":\"string\"}");
        export.getDocs().get(0).getResponsesSchemas().add(response);
        projects.importUpdateProject(projects.getById(project.getId()), export, options);
        ImportPreviewVo view = preview(payload("Same"));
        assertEquals("updated", docChange(view).getStatus());
        assertTrue(docChange(view).getChangedFields().contains("responsesSchemas"));
        assertTrue(projects.importUpdateProject(projects.getById(project.getId()), payload("Same"), options).isSuccess());
        assertTrue(details.loadDetailList(docs.loadByProject(project.getId())).get(0).getResponsesSchemas().isEmpty());
    }


    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void zipPreviewKeepsImagesUnwrittenAndUrlConfirmationRestoresThem(boolean urlMode) throws Exception {
        options.setSourceType("markdown");
        options.setFileName("docs.zip");
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("guide/readme.md"));
            zip.write("# Guide\n![picture](image.png)".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("guide/image.png"));
            zip.write(image);
            zip.closeEntry();
        }
        String before = database();
        DocSourceData source = DocSourceData.ofBinary(output.toByteArray(), "docs.zip");
        options.setImportType(urlMode ? "url" : "file");
        options.setUrl(urlMode ? "https://example.test/docs.zip" : null);
        when(urlContent.getContent(any())).thenReturn(SimpleResultUtils.createSimpleResult(source));
        SimpleResult<ImportPreviewVo> result = urlMode ? imports.loadImportPreview(options, new MockHttpServletRequest())
                : previews.preview(options, () -> projects.processImportProject(source, options));
        assertTrue(result.isSuccess(), result.getMessage());
        ImportPreviewVo view = result.getResultData();
        assertTrue(view.getChanges().stream().anyMatch(c -> ApiDocConstants.DOC_TYPE_MD.equals(c.getType())));
        assertEquals(before, database());
        Path path = Paths.get(assets.getBaseUploadPath(), "docs", "zip_import",
                org.apache.commons.codec.digest.DigestUtils.md5Hex(image) + ".png");
        assertFalse(Files.exists(path));
        if (urlMode) {
            try {
                // 修改下载返回值不会污染缓存中的二进制快照。
                Arrays.fill(source.getBinaryContent(), (byte) 0);
                assertTrue(imports.importExistsProject(options, new MockHttpServletRequest()).isSuccess());
                assertArrayEquals(image, Files.readAllBytes(path));
                assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("/upload/docs/zip_import/"));
                org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(1)).getContent(any());
            } finally {
                Files.deleteIfExists(path);
            }
        }
    }


    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newProjectFileImportStillWorksWithoutPreview(boolean taskOptions) throws Exception {
        when(access.canAccessImportGroup(any(), any())).thenReturn(true);
        ApiProjectImportVo createOptions = taskOptions ? new ApiProjectTaskImportVo() : new ApiProjectImportVo();
        createOptions.setImportType(ApiDocConstants.IMPORT_TYPE_FILE);
        createOptions.setSourceType("markdown");
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.addFile(new MockMultipartFile("files", "guide.md", "text/markdown",
                ("# New guide\n" + embeddedImage(image)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        SimpleResult<ApiProject> result = imports.importProject(createOptions, request);
        assertTrue(result.isSuccess(), result.getMessage());
        ApiProject created = result.getResultData();
        String url = imageUrl(created, image);
        try {
            assertNotEquals(project.getId(), created.getId());
            assertNotNull(created.getProjectCode());
            assertFalse(created.getProjectCode().isEmpty());
            assertNotEquals(project.getProjectCode(), created.getProjectCode());
            List<ApiDoc> saved = docs.loadByProject(created.getId());
            assertEquals(1, saved.size());
            assertTrue(saved.get(0).getDocContent().contains(url));
            assertArrayEquals(image, Files.readAllBytes(imagePath(url)));
            assertNotNull(folders.getRootFolder(created.getId()));
        } finally {
            projects.deleteApiProject(created.getId());
            Files.deleteIfExists(imagePath(url));
        }
    }

    @Test void scheduledImportStillDownloadsAndUpdatesWithoutPreviewAndKeepsFailureTracking() throws Exception {
        ApiProjectTask task = new ApiProjectTask();
        task.setProjectId(project.getId());
        task.setTaskName("Scheduled import");
        task.setTaskType(ApiDocConstants.PROJECT_TASK_TYPE_AUTO);
        task.setScheduleRate(600);
        task.setSourceType("markdown");
        task.setSourceUrl("https://example.test/guide.md");
        tasks.save(task);
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String imageMarkdown = embeddedImage(image);
        String url = imageUrl(project, image);
        when(urlContent.getContent(any(UrlWithAuthVo.class))).thenReturn(
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nFirst\n" + imageMarkdown)),
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nSecond\n" + imageMarkdown)),
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Guide\nSecond\n" + imageMarkdown)),
                SimpleResultUtils.createError("Download failed"));
        RequestContextHolder.resetRequestAttributes();
        try {
            com.fugary.simple.api.tasks.SimpleAutoTask<ApiProjectTask> scheduled = SimpleTaskUtils.projectTask2SimpleTask(task, taskInvoker, null);
            scheduled.triggerNow();
            assertEquals(1, docs.loadByProject(project.getId()).size());
            assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("First"));
            assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains(url));
            assertArrayEquals(image, Files.readAllBytes(imagePath(url)));
            assertNotNull(tasks.getById(task.getId()).getExecDate());
            assertNotNull(scheduled.getTaskWrapper().getLastExecDate());
            assertEquals(ApiDocConstants.SUCCESS, events.logs.get(events.logs.size() - 1).getLogResult());
            scheduled.triggerNow();
            assertTrue(docs.loadByProject(project.getId()).get(0).getDocContent().contains("Second"));
            ApiDoc updated = docs.loadByProject(project.getId()).get(0);
            scheduled.triggerNow();
            assertEquals(updated.getDocContent(), docs.getById(updated.getId()).getDocContent());
            assertEquals(updated.getVersion(), docs.getById(updated.getId()).getVersion());
            ImportStatisticsVo stats = JsonUtils.fromJson(events.logs.get(events.logs.size() - 1).getLogData(), ImportStatisticsVo.class);
            assertEquals(0, stats.getDocUpdated());
            assertEquals(1, stats.getDocUnchanged());
            String before = database();
            scheduled.triggerNow();
            assertEquals(before, database());
            assertEquals(ApiDocConstants.FAIL, events.logs.get(events.logs.size() - 1).getLogResult());
            org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(4)).getContent(any(UrlWithAuthVo.class));
        } finally {
            login("preview-user");
            Files.deleteIfExists(imagePath(url));
        }
    }


    @ParameterizedTest
    @CsvSource({"false, true", "false, false", "true, true", "true, false"})
    void mixedTaggedAndUntaggedImportsReuseFolders(boolean nestedTarget, boolean untaggedFirst) {
        ApiFolder target = importTarget(nestedTarget);
        String targetBefore = JsonUtils.toJson(target);
        String initial = mixedOpenApi(untaggedFirst, false);
        ImportPreviewVo first = preview(parseOpenApi(initial));
        assertEquals(2, first.getChanges().stream().filter(c -> "folder".equals(c.getType())).count());
        assertEquals(2, importOpenApi(initial).getFolderAdded());
        Map<String, ApiFolder> originalFolders = folders.calcFolderMap(folders.loadApiFolders(project.getId())).getLeft();
        ApiFolder pets = originalFolders.values().stream().filter(f -> "Pets".equals(f.getFolderCode())).findFirst().orElseThrow();
        pets.setFolderName("Custom pets");
        pets.setSortId(900);
        folders.updateById(pets);

        String updated = mixedOpenApi(untaggedFirst, true);
        String before = database();
        ImportPreviewVo view = preview(parseOpenApi(updated));
        assertEquals(before, database());
        assertEquals(0, view.getChanges().stream().filter(c -> "folder".equals(c.getType()) && !"unchanged".equals(c.getStatus())).count());
        assertEquals(3, view.getChanges().stream().filter(c -> "api".equals(c.getType()) && "added".equals(c.getStatus())).count());
        ImportStatisticsVo stats = importOpenApi(updated);
        assertEquals(0, stats.getFolderAdded());
        assertEquals(3, stats.getDocAdded());
        assertEquals(3, stats.getDocUnchanged());
        assertMixedImportFolders(target, originalFolders, 6);
        assertEquals("Custom pets", folders.getById(pets.getId()).getFolderName());
        assertEquals(900, folders.getById(pets.getId()).getSortId());
        assertEquals(targetBefore, JsonUtils.toJson(folders.getById(target.getId())));

        // 调整来源接口顺序后再次导入，也不能生成重复目录或移动文档。
        String reordered = mixedOpenApi(!untaggedFirst, true);
        ImportPreviewVo repeated = preview(parseOpenApi(reordered));
        assertTrue(repeated.getChanges().stream().filter(c -> "api".equals(c.getType()) || "folder".equals(c.getType()))
                .allMatch(c -> "unchanged".equals(c.getStatus())));
        ImportStatisticsVo repeatedStats = importOpenApi(reordered);
        assertEquals(0, repeatedStats.getFolderAdded());
        assertEquals(6, repeatedStats.getDocUnchanged());
        assertMixedImportFolders(target, originalFolders, 6);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scheduledMixedImportReusesTargetAndTaggedFolders(boolean nestedTarget) {
        ApiFolder target = importTarget(nestedTarget);
        ApiProjectTask task = new ApiProjectTask();
        task.setProjectId(project.getId());
        task.setToFolder(options.getToFolder());
        task.setTaskName("Mixed OpenAPI sync");
        task.setTaskType(ApiDocConstants.PROJECT_TASK_TYPE_AUTO);
        task.setScheduleRate(600);
        task.setSourceType("openapi");
        task.setSourceUrl("https://example.test/mixed.yaml");
        tasks.save(task);
        when(urlContent.getContent(any(UrlWithAuthVo.class))).thenReturn(
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText(mixedOpenApi(true, false))),
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText(mixedOpenApi(true, true))));
        RequestContextHolder.resetRequestAttributes();
        try {
            com.fugary.simple.api.tasks.SimpleAutoTask<ApiProjectTask> scheduled = SimpleTaskUtils.projectTask2SimpleTask(task, taskInvoker, null);
            scheduled.triggerNow();
            Map<String, ApiFolder> originalFolders = folders.calcFolderMap(folders.loadApiFolders(project.getId())).getLeft();
            assertMixedImportFolders(target, originalFolders, 3);
            scheduled.triggerNow();
            assertMixedImportFolders(target, originalFolders, 6);
            scheduled.triggerNow();
            assertMixedImportFolders(target, originalFolders, 6);
            assertNotNull(tasks.getById(task.getId()).getExecDate());
            assertNotNull(scheduled.getTaskWrapper().getLastExecDate());
            assertEquals(3, events.logs.size());
            assertTrue(events.logs.stream().allMatch(log -> ApiDocConstants.SUCCESS.equals(log.getLogResult())));
            org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(3)).getContent(any(UrlWithAuthVo.class));
        } finally {
            login("preview-user");
        }
    }

    private ApiFolder importTarget(boolean nested) {
        ApiFolder target = folders.getRootFolder(project.getId());
        if (nested) {
            ApiFolder child = new ApiFolder();
            child.setProjectId(project.getId());
            child.setParentId(target.getId());
            child.setFolderName("Import target");
            child.setFolderCode("target");
            child.setStatus(ApiDocConstants.STATUS_ENABLED);
            folders.save(child);
            target = folders.getById(child.getId());
            options.setToFolder(target.getId());
        }
        return target;
    }

    private String mixedOpenApi(boolean untaggedFirst, boolean updated) {
        String untagged = openApiOperation("/health", "");
        String tagged = openApiOperation("/pets", "      tags: [Pets]\n")
                + openApiOperation("/details", "      tags: [Pets]\n      x-simple-folder: Pets/Details\n");
        if (updated) {
            untagged += openApiOperation("/health/new", "");
            tagged += openApiOperation("/pets/new", "      tags: [Pets]\n")
                    + openApiOperation("/details/new", "      tags: [Pets]\n      x-simple-folder: Pets/Details\n");
        }
        return "openapi: 3.0.1\ninfo:\n  title: Mixed API\n  version: 1.0.0\npaths:\n"
                + (untaggedFirst ? untagged + tagged : tagged + untagged);
    }

    private String openApiOperation(String path, String grouping) {
        return "  " + path + ":\n    get:\n      summary: " + path + "\n" + grouping
                + "      responses:\n        '200':\n          description: OK\n";
    }

    private ExportApiProjectVo parseOpenApi(String yaml) {
        SimpleResult<ExportApiProjectVo> parsed = projects.processImportProject(DocSourceData.ofText(yaml), options);
        assertTrue(parsed.isSuccess(), parsed.getMessage());
        return parsed.getResultData();
    }

    private ImportStatisticsVo importOpenApi(String yaml) {
        SimpleResult<ApiProject> result = projects.importUpdateProject(projects.getById(project.getId()), parseOpenApi(yaml), options);
        assertTrue(result.isSuccess(), result.getMessage());
        return (ImportStatisticsVo) result.getAddons().get("statistics");
    }

    private void assertMixedImportFolders(ApiFolder target, Map<String, ApiFolder> originalFolders, int docCount) {
        List<ApiFolder> saved = folders.loadApiFolders(project.getId());
        assertEquals(originalFolders.size(), saved.size(), "Mixed imports must not duplicate tagged folders");
        Map<String, ApiFolder> current = folders.calcFolderMap(saved).getLeft();
        assertEquals(originalFolders.keySet(), current.keySet());
        originalFolders.forEach((path, folder) -> assertEquals(folder.getId(), current.get(path).getId()));
        String mountPath = folders.calcFolderMap(saved).getRight().get(target.getId());
        List<ApiDoc> imported = docs.loadByProject(project.getId());
        assertEquals(docCount, imported.size());
        for (ApiDoc doc : imported) {
            String relative = doc.getUrl().startsWith("/health") ? "" : doc.getUrl().startsWith("/pets") ? "/Pets" : "/Pets/Details";
            assertEquals(current.get(mountPath + relative).getId(), doc.getFolderId(), doc.getUrl());
        }
    }

    @Test void sourceDisplayKeepsGroupSelectionAndMasksCredentials() {
        assertEquals("https://***@example.test/docs?group=orders&token=***&%61pi_key=***",
                ImportPreviewVo.safeSource("https://user:password@example.test/docs?group=orders&token=secret&%61pi_key=secret"));
    }


    @Test void savedTaskPreviewDoesNotChangeExecutionTimeOrLogs() {
        when(access.canAccessTask(any(ApiProjectTask.class), any())).thenReturn(true);
        ApiProjectTask task = new ApiProjectTask();
        task.setProjectId(project.getId());
        task.setTaskName("Manual import");
        task.setTaskType(ApiDocConstants.PROJECT_TASK_TYPE_MANUAL);
        task.setSourceType("markdown");
        task.setSourceUrl("https://example.test/task.md");
        tasks.save(task);
        options.setTaskId(task.getId());
        when(urlContent.getContent(any(UrlWithAuthVo.class))).thenReturn(
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Task\nContent")));
        assertTrue(imports.loadImportPreview(options, new MockHttpServletRequest()).isSuccess());
        assertTrue(events.logs.isEmpty());
        assertNull(tasks.getById(task.getId()).getExecDate());
        assertTrue(docs.loadByProject(project.getId()).isEmpty());
        assertTrue(taskInvoker.importProject(task, options.getPreviewKey()).isSuccess());
        org.mockito.Mockito.verify(urlContent, org.mockito.Mockito.times(1)).getContent(any());
        assertNotNull(tasks.getById(task.getId()).getExecDate());
        assertEquals(1, docs.loadByProject(project.getId()).size());
    }

    @Test void diffIgnoresInternalSchemaStatusAndObjectOrderButKeepsBusinessChanges() {
        Map<String, Object> before = Map.of("bodyType", "component", "schemaContent",
                "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\"},\"a\":{\"type\":\"integer\"}}}");
        Map<String, Object> after = Map.of("bodyType", "component", "status", 1, "enabled", true, "schemaContent",
                "{\"properties\":{\"a\":{\"type\":\"integer\"},\"status\":{\"type\":\"string\"}},\"type\":\"object\"}");
        ImportPreviewVo view = new ImportPreviewVo();
        assertEquals("unchanged", view.record("model", "Model", "component", null, before, after).getStatus());
        Map<String, Object> changed = new HashMap<>(after);
        changed.put("schemaContent", "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"boolean\"}}}");
        ImportPreviewVo.Change diff = view.record("model", "Model", "component", null, before, changed);
        assertEquals("updated", diff.getStatus());
        assertEquals(List.of("schemaContent"), diff.getChangedFields());
        assertTrue(diff.getModified().contains("\"status\""));
        assertFalse(diff.getModified().contains("\"enabled\""));
        assertEquals("updated", view.record("model", "Text", "component", null,
                Map.of("schemaContent", "{\"example\":\"a b\"}"), Map.of("schemaContent", "{\"example\":\"ab\"}")).getStatus());
        assertEquals("updated", view.record("model", "Array", "component", null,
                Map.of("schemaContent", "{\"example\":[1,2]}"), Map.of("schemaContent", "{\"example\":[2,1]}")).getStatus());
    }

    @Test void failedParsingDoesNotLeaveImageSavingDisabled() throws Exception {
        assertThrows(IllegalStateException.class, () -> previews.preview(options, () -> { throw new IllegalStateException("Parse failure"); }));
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url = assets.saveImage(image, "normal.png", project.getProjectCode());
        Path path = Paths.get(assets.getBaseUploadPath(), url.substring("/upload/".length()));
        assertTrue(Files.exists(path));
        Files.delete(path);
    }
    @Test void credentialsOnlyEnvironmentChangeIsStillImported() {
        ExportApiProjectVo original = payload("Same");
        original.setEnvContent("[{\"name\":\"Test\",\"url\":\"https://example.test?token=old\"}]");
        projects.importUpdateProject(projects.getById(project.getId()), original, options);
        ExportApiProjectVo changed = payload("Same");
        changed.setEnvContent("[{\"name\":\"Test\",\"url\":\"https://example.test?token=new\"}]");
        ImportPreviewVo view = preview(changed);
        int index = java.util.stream.IntStream.range(0, view.getChanges().size())
                .filter(i -> "project".equals(view.getChanges().get(i).getType())).findFirst().orElseThrow();
        assertEquals("updated", view.getChanges().get(index).getStatus());
        ImportPreviewVo.Change detail = view.getChanges().get(index);
        assertFalse(detail.getOriginal().contains("token=old"));
        assertFalse(detail.getModified().contains("token=new"));
        ExportApiProjectVo fresh = payload("Same");
        fresh.setEnvContent("[{\"name\":\"Test\",\"url\":\"https://example.test?token=new\"}]");
        assertTrue(projects.importUpdateProject(projects.getById(project.getId()), fresh, options).isSuccess());
        assertTrue(projects.getById(project.getId()).getEnvContent().contains("token=new"));
    }


    @Test void missingRootAndEmptyFoldersUseTheSameRulesAsDirectImport() {
        folders.deleteByProject(project.getId());
        ExportApiProjectVo export = payload("Root doc");
        ExportApiFolderVo empty = new ExportApiFolderVo();
        empty.setFolderName("Empty");
        empty.setFolderCode("empty");
        empty.setFolderPath("empty");
        export.getFolders().add(empty);
        String before = database();
        String source = JsonUtils.toJson(export);
        ImportPreviewVo view = preview(export);
        assertEquals(before, database());
        assertEquals(1, view.getChanges().stream().filter(c -> "folder".equals(c.getType())).count());
        assertTrue(projects.importUpdateProject(projects.getById(project.getId()), JsonUtils.fromJson(source, ExportApiProjectVo.class), options).isSuccess());
        assertEquals(1, folders.loadApiFolders(project.getId()).size());
        assertEquals(folders.getRootFolder(project.getId()).getId(), docs.loadByProject(project.getId()).get(0).getFolderId());
    }


    @Test void schemaCollectionOrderDoesNotCreateDiffs() {
        Map<String, Object> json = Map.of("bodyType", "response", "schemaName", "200", "contentType", "application/json", "schemaContent", "{\"type\":\"string\"}");
        Map<String, Object> xml = Map.of("bodyType", "response", "schemaName", "200", "contentType", "application/xml", "schemaContent", "{\"type\":\"string\"}");
        ImportPreviewVo view = new ImportPreviewVo();
        assertEquals("unchanged", view.record("api", "Doc", "/doc", null,
                Map.of("responsesSchemas", List.of(json, xml)), Map.of("responsesSchemas", List.of(xml, json))).getStatus());
    }

    private ExportApiProjectVo withResponse(String schema) {
        ExportApiProjectVo source = payload("Same");
        ExportApiDocSchemaVo response = new ExportApiDocSchemaVo();
        response.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE);
        response.setSchemaName("200");
        response.setContentType("application/json");
        response.setSchemaContent(schema);
        source.getDocs().get(0).getResponsesSchemas().add(response);
        return source;
    }

    @Test void previewAndImportAgreeOnSchemaKeyOrderUpdates() {
        String original = "{\"type\":\"object\",\"description\":\"Example\"}";
        String reordered = "{\"description\":\"Example\",\"type\":\"object\"}";
        projects.importUpdateProject(projects.getById(project.getId()), withResponse(original), options);
        ImportPreviewVo.Change change = docChange(preview(withResponse(reordered)));
        SimpleResult<ApiProject> imported = projects.importUpdateProject(projects.getById(project.getId()), withResponse(reordered), options);
        ImportStatisticsVo stats = (ImportStatisticsVo) imported.getAddons().get("statistics");
        assertEquals(1, stats.getDocUpdated());
        assertEquals("updated", change.getStatus());
        assertTrue(change.isFormatOnly());
        assertNotEquals(change.getOriginal(), change.getModified());
        assertTrue(change.getOriginal().indexOf("\"type\"") < change.getOriginal().indexOf("\"description\" : \"Example\""));
        assertTrue(change.getModified().indexOf("\"type\"") > change.getModified().indexOf("\"description\" : \"Example\""));
    }

    @Test void removedSchemasAreAlsoCountedByNormalImport() {
        projects.importUpdateProject(projects.getById(project.getId()), withResponse("{\"type\":\"string\"}"), options);
        assertEquals("updated", docChange(preview(payload("Same"))).getStatus());
        SimpleResult<ApiProject> imported = projects.importUpdateProject(projects.getById(project.getId()), payload("Same"), options);
        assertEquals(1, ((ImportStatisticsVo) imported.getAddons().get("statistics")).getDocUpdated());
    }

    @Test void savedTaskPreviewUsesTheSameSourceMetadataAsTaskImport() {
        when(access.canAccessTask(any(ApiProjectTask.class), any())).thenReturn(true);
        ApiProjectTask task = new ApiProjectTask();
        task.setProjectId(project.getId());
        task.setTaskName("Task source metadata");
        task.setTaskType(ApiDocConstants.PROJECT_TASK_TYPE_MANUAL);
        task.setSourceType("markdown");
        task.setSourceUrl("https://example.test/task.md");
        tasks.save(task);
        ApiProjectTaskImportVo taskOptions = new ApiProjectTaskImportVo();
        taskOptions.setTaskId(task.getId());
        when(urlContent.getContent(any(UrlWithAuthVo.class))).thenReturn(
                SimpleResultUtils.createSimpleResult(DocSourceData.ofText("# Task\nContent")));
        ImportPreviewVo view = imports.loadImportPreview(taskOptions, new MockHttpServletRequest()).getResultData();
        ImportPreviewVo.Change source = view.getChanges().stream().filter(item -> "source".equals(item.getType())).findFirst().orElseThrow();
        assertTrue(taskInvoker.importProject(task).isSuccess());
        ApiProjectInfo saved = infos.loadByProjectId(project.getId()).get(0);
        com.fasterxml.jackson.databind.JsonNode displayed = JsonUtils.fromJson(source.getModified(), com.fasterxml.jackson.databind.JsonNode.class);
        assertEquals(saved.getFileName(), displayed.path("fileName").asText(null));
        assertEquals(saved.getUrl(), displayed.path("url").asText());
    }

    @Test void businessJsonStringsAreNotDecodedAsEntityFields() {
        ImportPreviewVo view = new ImportPreviewVo();
        ImportPreviewVo.Change change = view.record("model", "Example", "component", null,
                Map.of("schemaContent", "{\"example\":{\"examples\":\"[1,2]\"}}"),
                Map.of("schemaContent", "{\"example\":{\"examples\":[1,2]}}"));
        assertEquals("updated", change.getStatus());
    }

    @Test void responseDescriptionIsReportedAndCountedAsUpdate() {
        ExportApiProjectVo original = withResponse("{\"type\":\"string\"}");
        original.getDocs().get(0).getResponsesSchemas().get(0).setDescription("Old response");
        projects.importUpdateProject(projects.getById(project.getId()), original, options);
        ExportApiProjectVo incoming = withResponse("{\"type\":\"string\"}");
        incoming.getDocs().get(0).getResponsesSchemas().get(0).setDescription("New response");
        String source = JsonUtils.toJson(incoming);
        ImportPreviewVo.Change change = docChange(preview(incoming));
        assertEquals("updated", change.getStatus());
        assertEquals(List.of("responsesSchemas"), change.getChangedFields());
        SimpleResult<ApiProject> imported = projects.importUpdateProject(projects.getById(project.getId()),
                JsonUtils.fromJson(source, ExportApiProjectVo.class), options);
        assertEquals(1, ((ImportStatisticsVo) imported.getAddons().get("statistics")).getDocUpdated());
        assertEquals("New response", details.loadDetailList(docs.loadByProject(project.getId())).get(0).getResponsesSchemas().get(0).getDescription());
    }

    private ExportApiProjectVo withModel(String schema, boolean locked) {
        ExportApiProjectVo source = payload("Same");
        ExportApiProjectInfoDetailVo model = new ExportApiProjectInfoDetailVo();
        model.setBodyType(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT);
        model.setSchemaName("Pet");
        model.setSchemaContent(schema);
        if (locked) { model.setLocked(true); }
        source.getProjectInfoDetails().add(model);
        return source;
    }

    @Test void previewUsesActualLockedModelMergeResult() {
        String original = "{\"type\":\"object\",\"properties\":{\"oldField\":{\"type\":\"string\"}}}";
        String incoming = "{\"type\":\"object\",\"properties\":{\"newField\":{\"type\":\"integer\"}}}";
        projects.importUpdateProject(projects.getById(project.getId()), withModel(original, true), options);
        ImportPreviewVo.Change change = preview(withModel(incoming, false)).getChanges().stream()
                .filter(item -> "model".equals(item.getType())).findFirst().orElseThrow();
        assertEquals("updated", change.getStatus());
        assertTrue(change.getModified().contains("oldField"));
        assertTrue(change.getModified().contains("newField"));
        projects.importUpdateProject(projects.getById(project.getId()), withModel(incoming, false), options);
        ApiProjectInfoDetail saved = details.loadByProject(project.getId(), Set.of(ApiDocConstants.PROJECT_SCHEMA_TYPE_COMPONENT)).get(0);
        com.fasterxml.jackson.databind.JsonNode displayed = JsonUtils.fromJson(change.getModified(), com.fasterxml.jackson.databind.JsonNode.class);
        assertEquals(JsonUtils.fromJson(saved.getSchemaContent(), com.fasterxml.jackson.databind.JsonNode.class), displayed.get("schemaContent"));
        assertTrue(saved.getLocked());
    }

    @Test void schemaDefaultStatusAndResponseCollectionOrderStayUnchanged() {
        ExportApiProjectVo original = withResponse("{\"type\":\"string\"}");
        ExportApiDocSchemaVo xml = new ExportApiDocSchemaVo();
        xml.setBodyType(ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE);
        xml.setSchemaName("200");
        xml.setContentType("application/xml");
        xml.setSchemaContent("{\"type\":\"string\"}");
        original.getDocs().get(0).getResponsesSchemas().add(xml);
        String source = JsonUtils.toJson(original);
        projects.importUpdateProject(projects.getById(project.getId()), original, options);
        ExportApiProjectVo incoming = JsonUtils.fromJson(source, ExportApiProjectVo.class);
        Collections.reverse(incoming.getDocs().get(0).getResponsesSchemas());
        incoming.getDocs().get(0).getResponsesSchemas().forEach(schema -> schema.setStatus(ApiDocConstants.STATUS_ENABLED));
        source = JsonUtils.toJson(incoming);
        assertEquals("unchanged", docChange(preview(incoming)).getStatus());
        SimpleResult<ApiProject> imported = projects.importUpdateProject(projects.getById(project.getId()),
                JsonUtils.fromJson(source, ExportApiProjectVo.class), options);
        assertEquals(1, ((ImportStatisticsVo) imported.getAddons().get("statistics")).getDocUnchanged());
    }


    @Test void sourceDefaultStatusIsNotPersistedOrReportedAsChange() {
        ExportApiProjectVo original = payload("Same");
        original.getProjectInfo().setStatus(ApiDocConstants.STATUS_ENABLED);
        projects.importUpdateProject(projects.getById(project.getId()), original, options);
        assertNull(infos.loadByProjectId(project.getId()).get(0).getStatus());
        ExportApiProjectVo incoming = payload("Same");
        incoming.getProjectInfo().setStatus(ApiDocConstants.STATUS_ENABLED);
        ImportPreviewVo.Change source = preview(incoming).getChanges().stream()
                .filter(item -> "source".equals(item.getType())).findFirst().orElseThrow();
        assertEquals("unchanged", source.getStatus());
        incoming = payload("Same");
        incoming.getProjectInfo().setStatus(ApiDocConstants.STATUS_ENABLED);
        incoming.getProjectInfo().setVersion("2.0");
        source = preview(incoming).getChanges().stream()
                .filter(item -> "source".equals(item.getType())).findFirst().orElseThrow();
        assertEquals(List.of("version"), source.getChangedFields());
        assertFalse(source.getModified().contains("\"status\""));
        assertFalse(source.getModified().contains("\"enabled\""));
        projects.importUpdateProject(projects.getById(project.getId()), incoming, options);
        assertEquals("2.0", infos.loadByProjectId(project.getId()).get(0).getVersion());
        assertNull(infos.loadByProjectId(project.getId()).get(0).getStatus());
        ImportPreviewVo view = new ImportPreviewVo();
        for (String type : List.of("api", "folder", "project")) {
            assertEquals(List.of("status"), view.record(type, "Name", "", null,
                    Map.of("status", 0), Map.of("status", 1)).getChangedFields());
        }
    }

    @Test void whitespaceOnlySchemaUpdatesHaveInspectableStoredText() {
        String original = "{\"type\":\"string\"}";
        String spaced = "{ \"type\" : \"string\" }";
        projects.importUpdateProject(projects.getById(project.getId()), withResponse(original), options);
        ImportPreviewVo.Change change = docChange(preview(withResponse(spaced)));
        assertTrue(change.isFormatOnly());
        assertNotEquals(change.getOriginal(), change.getModified());
        assertEquals(original, JsonUtils.fromJson(change.getOriginal(), com.fasterxml.jackson.databind.JsonNode.class)
                .path("responsesSchemas").get(0).path("schemaContent").asText());
        assertEquals(spaced, JsonUtils.fromJson(change.getModified(), com.fasterxml.jackson.databind.JsonNode.class)
                .path("responsesSchemas").get(0).path("schemaContent").asText());
        SimpleResult<ApiProject> imported = projects.importUpdateProject(projects.getById(project.getId()), withResponse(spaced), options);
        assertEquals(1, ((ImportStatisticsVo) imported.getAddons().get("statistics")).getDocUpdated());
        ImportPreviewVo.Change masked = new ImportPreviewVo().record("model", "Secret", "component", "updated",
                Map.of("schemaContent", "{\"token\":\"sensitive\"}"),
                Map.of("schemaContent", "{ \"token\" : \"sensitive\" }"));
        assertTrue(masked.isFormatOnly());
        assertFalse(masked.getOriginal().contains("sensitive"));
        assertFalse(masked.getModified().contains("sensitive"));
    }
}
