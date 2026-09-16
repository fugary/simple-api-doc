package com.fugary.simple.api.exports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.exports.md.MarkdownZipApiDocExporterImpl;
import com.fugary.simple.api.exports.md.MarkdownApiDocExporterImpl;
import com.fugary.simple.api.exports.md.ApiDocFreemarkerUtils;
import com.fugary.simple.api.exports.md.MarkdownApiDocViewGeneratorImpl;
import com.fugary.simple.api.exports.md.MdViewContext;
import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.service.impl.apidoc.asset.DocAssetStorageServiceImpl;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import freemarker.template.Configuration;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class MarkdownZipApiDocExporterTest {

    private MarkdownZipApiDocExporterImpl exporter;
    private ApiProjectService mockProjectService;
    private ApiProjectInfoDetailService mockProjectInfoDetailService;
    private DocAssetStorageService mockAssetStorageService;

    @BeforeEach
    public void setup() {
        exporter = new MarkdownZipApiDocExporterImpl();

        mockProjectService = Mockito.mock(ApiProjectService.class);
        mockProjectInfoDetailService = Mockito.mock(ApiProjectInfoDetailService.class);
        mockAssetStorageService = Mockito.mock(DocAssetStorageService.class);

        ApiDocViewGenerator mockViewGenerator = Mockito.mock(ApiDocViewGenerator.class);
        Mockito.when(mockViewGenerator.generate(any())).thenReturn("### 接口详情与参数说明");

        DocAssetStorageServiceImpl realAssetService = new DocAssetStorageServiceImpl();
        Mockito.when(mockAssetStorageService.resolveImageFile(any(), any(), any()))
                .thenAnswer(inv -> realAssetService.resolveImageFile(mockAssetStorageService.getBaseUploadPath(),
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        ReflectionTestUtils.setField(exporter, "apiProjectService", mockProjectService);
        ReflectionTestUtils.setField(exporter, "apiProjectInfoDetailService", mockProjectInfoDetailService);
        ReflectionTestUtils.setField(exporter, "apiDocViewGenerator", mockViewGenerator);
        ReflectionTestUtils.setField(exporter, "docAssetStorageService", mockAssetStorageService);
    }

    @Test
    public void testExportMultiLevelZip() throws IOException, TemplateModelException {
        int projectId = 100;
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode("test-proj");
        project.setProjectName("测试项目");
        project.setApiVersion("1.0.0");
        project.setDescription("这是一个测试文档项目");

        // 根文件夹
        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("root");
        rootFolder.setRootFlag(true);

        // 一级文件夹: 指南
        ApiFolder guideFolder = new ApiFolder();
        guideFolder.setId(2);
        guideFolder.setFolderName("guide");
        guideFolder.setParentId(1);
        guideFolder.setSortId(10);

        // 二级文件夹: 进阶
        ApiFolder advancedFolder = new ApiFolder();
        advancedFolder.setId(3);
        advancedFolder.setFolderName("advanced");
        advancedFolder.setParentId(2);
        advancedFolder.setSortId(20);

        project.setFolders(List.of(rootFolder, guideFolder, advancedFolder));

        // 文档 1: 根目录 README
        ApiDocDetailVo doc1 = new ApiDocDetailVo();
        doc1.setId(101);
        doc1.setFolderId(1);
        doc1.setDocType(ApiDocConstants.DOC_TYPE_MD);
        doc1.setDocName("README");
        doc1.setSortId(1);
        doc1.setDocContent("# 欢迎使用测试系统\n\n这是根目录说明文档。");

        // 文档 2: guide/install.md
        ApiDocDetailVo doc2 = new ApiDocDetailVo();
        doc2.setId(102);
        doc2.setFolderId(2);
        doc2.setDocType(ApiDocConstants.DOC_TYPE_MD);
        doc2.setDocName("快速上手");
        doc2.setSortId(100);
        doc2.setDescription("快速入门与安装说明");
        doc2.setDocContent("# 快速上手\n\n请按照步骤安装依赖。");

        // 文档 3: guide/advanced/auth.md (带废弃与锁定标记)
        ApiDocDetailVo doc3 = new ApiDocDetailVo();
        doc3.setId(103);
        doc3.setFolderId(3);
        doc3.setDocType(ApiDocConstants.DOC_TYPE_MD);
        doc3.setDocName("鉴权说明");
        doc3.setSortId(200);
        doc3.setDeprecated(true);
        doc3.setLocked(true);
        doc3.setDocContent("# 鉴权说明\n\n采用 JWT Bearer 认证。");

        project.setDocs(List.of(doc1, doc2, doc3));

        ApiProjectInfoDetailVo markdownInfo = new ApiProjectInfoDetailVo();
        markdownInfo.setSpecVersion(ApiDocConstants.SOURCE_TYPE_MARKDOWN);
        Mockito.when(mockProjectInfoDetailService.mergeInfoDetailVo(any())).thenReturn(markdownInfo);
        Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
        Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(doc1, doc2, doc3));
        Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());

        ExportDownloadVo downloadVo = new ExportDownloadVo();
        downloadVo.setType("zip");

        byte[] zipBytes = exporter.export(projectId, downloadVo);
        Assertions.assertNotNull(zipBytes);
        Assertions.assertTrue(zipBytes.length > 0);

        // 验证 ZIP 内部 Entry 结构与内容
        Map<String, String> zipContents = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(IOUtils.toByteArray(zis), StandardCharsets.UTF_8);
                zipContents.put(entry.getName(), content);
                zis.closeEntry();
            }
        }

        Assertions.assertTrue(zipContents.containsKey("README.md"));
        Assertions.assertTrue(zipContents.containsKey("guide/快速上手.md"));
        Assertions.assertTrue(zipContents.containsKey("guide/advanced/鉴权说明.md"));

        // 检查 Frontmatter 元数据
        String authDocContent = zipContents.get("guide/advanced/鉴权说明.md");
        Assertions.assertTrue(authDocContent.contains("title: \"鉴权说明\"") || authDocContent.contains("title: 鉴权说明"));
        Assertions.assertTrue(authDocContent.contains("order: 200"));
        Assertions.assertTrue(authDocContent.contains("deprecated: true"));
        Assertions.assertTrue(authDocContent.contains("locked: true"));

        // 闭环验证：使用 MarkdownDocImporterImpl 反向解析导入
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        DocSourceData sourceData = DocSourceData.ofBinary(zipBytes, "exported.zip");
        Assertions.assertTrue(importer.match(sourceData));

        ExportApiProjectVo importedProject = importer.doImport(sourceData, null);
        Assertions.assertNotNull(importedProject);
        Assertions.assertEquals(1, importedProject.getDocs().size()); // 根目录 README
        Assertions.assertEquals(1, importedProject.getFolders().size()); // 一级目录 guide

        ExportApiFolderVo importedGuide = importedProject.getFolders().get(0);
        Assertions.assertEquals("guide", importedGuide.getFolderName());
        Assertions.assertEquals(1, importedGuide.getDocs().size()); // 快速上手
        Assertions.assertEquals("快速上手", importedGuide.getDocs().get(0).getDocName());

        Assertions.assertEquals(1, importedGuide.getFolders().size()); // 二级目录 advanced
        ExportApiFolderVo importedAdvanced = importedGuide.getFolders().get(0);
        Assertions.assertEquals("advanced", importedAdvanced.getFolderName());
        Assertions.assertEquals(1, importedAdvanced.getDocs().size()); // 鉴权说明

        ExportApiDocVo importedAuthDoc = importedAdvanced.getDocs().get(0);
        Assertions.assertEquals("鉴权说明", importedAuthDoc.getDocName());
        Assertions.assertEquals(200, importedAuthDoc.getSortId());
        Assertions.assertTrue(Boolean.TRUE.equals(importedAuthDoc.getDeprecated()));
        Assertions.assertTrue(Boolean.TRUE.equals(importedAuthDoc.getLocked()));

        // 同一纯 Markdown 项目也应支持合并导出为单个 Markdown 文件。
        MarkdownApiDocExporterImpl markdownExporter = new MarkdownApiDocExporterImpl();
        markdownExporter.setApiProjectService(mockProjectService);
        markdownExporter.setApiProjectInfoDetailService(mockProjectInfoDetailService);
        markdownExporter.setApiDocViewGenerator(Mockito.mock(ApiDocViewGenerator.class));
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassForTemplateLoading(getClass(), "/templates");
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setSharedVariable("utils", new ApiDocFreemarkerUtils());
        configuration.setSharedVariable("message", (TemplateMethodModelEx) arguments -> arguments.get(0).toString());
        markdownExporter.setFreemarkerConfig(configuration);

        String markdown = markdownExporter.export(projectId, downloadVo);
        Assertions.assertTrue(markdown.contains(doc1.getDocContent()));
        Assertions.assertTrue(markdown.contains(doc2.getDocContent()));
        Assertions.assertTrue(markdown.contains(doc3.getDocContent()));
    }

    @Test
    public void testExportWithDuplicateNamesAndApiDocs() throws IOException {
        int projectId = 101;
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode("test-api-proj");
        project.setProjectName("API混合项目");

        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("root");
        rootFolder.setRootFlag(true);

        ApiFolder userFolder = new ApiFolder();
        userFolder.setId(2);
        userFolder.setFolderName("user");
        userFolder.setParentId(1);

        project.setFolders(List.of(rootFolder, userFolder));

        // 同名文档测试
        ApiDocDetailVo doc1 = new ApiDocDetailVo();
        doc1.setId(201);
        doc1.setFolderId(2);
        doc1.setDocType(ApiDocConstants.DOC_TYPE_MD);
        doc1.setDocName("用户指南");
        doc1.setDocContent("# 用户指南 1");

        ApiDocDetailVo doc2 = new ApiDocDetailVo();
        doc2.setId(202);
        doc2.setFolderId(2);
        doc2.setDocType(ApiDocConstants.DOC_TYPE_MD);
        doc2.setDocName("用户指南");
        doc2.setDocContent("# 用户指南 2");

        // API 接口文档
        ApiDocDetailVo apiDoc = new ApiDocDetailVo();
        apiDoc.setId(203);
        apiDoc.setFolderId(2);
        apiDoc.setDocType(ApiDocConstants.DOC_TYPE_API);
        apiDoc.setDocName("获取用户列表");
        apiDoc.setMethod("GET");
        apiDoc.setUrl("/api/v1/users");

        project.setDocs(List.of(doc1, doc2, apiDoc));

        Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
        Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(doc1, doc2, apiDoc));
        Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());

        ExportDownloadVo downloadVo = new ExportDownloadVo();
        downloadVo.setType("zip");

        byte[] zipBytes = exporter.export(projectId, downloadVo);
        Assertions.assertNotNull(zipBytes);

        Map<String, String> zipContents = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(IOUtils.toByteArray(zis), StandardCharsets.UTF_8);
                zipContents.put(entry.getName(), content);
                zis.closeEntry();
            }
        }

        Assertions.assertTrue(zipContents.containsKey("user/用户指南.md"));
        Assertions.assertTrue(zipContents.containsKey("user/用户指南_1.md"));
        Assertions.assertTrue(zipContents.containsKey("user/获取用户列表.md"));
        Assertions.assertTrue(zipContents.containsKey("README.md")); // 自动生成根目录概览

        String apiDocContent = zipContents.get("user/获取用户列表.md");
        Assertions.assertTrue(apiDocContent.contains("# 获取用户列表"));
        Assertions.assertTrue(apiDocContent.contains("docType: \"api\"") || apiDocContent.contains("docType: api"));
        Assertions.assertTrue(apiDocContent.contains("method: \"GET\"") || apiDocContent.contains("method: GET"));
        Assertions.assertTrue(apiDocContent.contains("url: \"/api/v1/users\"") || apiDocContent.contains("url: /api/v1/users"));
    }

    @Test
    public void testExportWithoutFrontmatter() throws IOException {
        int projectId = 100;
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode("test-proj");
        project.setProjectName("测试文档项目");

        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("root");
        rootFolder.setRootFlag(true);

        ApiFolder guideFolder = new ApiFolder();
        guideFolder.setId(2);
        guideFolder.setFolderName("guide");
        guideFolder.setParentId(1);

        project.setFolders(List.of(rootFolder, guideFolder));

        ApiDocDetailVo doc1 = new ApiDocDetailVo();
        doc1.setId(10);
        doc1.setFolderId(2);
        doc1.setDocType(ApiDocConstants.DOC_TYPE_MD);
        doc1.setDocName("快速上手");
        doc1.setDocContent("# 快速上手\n\n欢迎使用本系统。");

        ApiDocDetailVo apiDoc = new ApiDocDetailVo();
        apiDoc.setId(11);
        apiDoc.setFolderId(2);
        apiDoc.setDocType(ApiDocConstants.DOC_TYPE_API);
        apiDoc.setDocName("登录接口");
        apiDoc.setMethod("POST");
        apiDoc.setUrl("/api/login");

        project.setDocs(List.of(doc1, apiDoc));

        Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
        Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(doc1, apiDoc));
        Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());

        ExportDownloadVo downloadVo = new ExportDownloadVo();
        downloadVo.setType("zip");
        downloadVo.setWithFrontmatter(false);

        byte[] zipBytes = exporter.export(projectId, downloadVo);
        Assertions.assertNotNull(zipBytes);

        Map<String, String> zipContents = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(IOUtils.toByteArray(zis), StandardCharsets.UTF_8);
                zipContents.put(entry.getName(), content);
                zis.closeEntry();
            }
        }

        Assertions.assertTrue(zipContents.containsKey("guide/快速上手.md"));
        Assertions.assertTrue(zipContents.containsKey("guide/登录接口.md"));
        Assertions.assertTrue(zipContents.containsKey("README.md"));

        // 验证没有 Frontmatter
        String guideContent = zipContents.get("guide/快速上手.md");
        Assertions.assertFalse(guideContent.startsWith("---"));
        Assertions.assertTrue(guideContent.startsWith("# 快速上手"));

        String apiContent = zipContents.get("guide/登录接口.md");
        Assertions.assertFalse(apiContent.startsWith("---"));
        Assertions.assertTrue(apiContent.startsWith("# 登录接口"));
        Assertions.assertFalse(apiContent.contains("docType:"));

        String readmeContent = zipContents.get("README.md");
        Assertions.assertFalse(readmeContent.startsWith("---"));
        Assertions.assertTrue(readmeContent.startsWith("# 测试文档项目"));
    }

    @Test
    public void testExportWithImageAssets() throws IOException {
        int projectId = 102;
        String projectCode = "img-proj";
        File tempUploadDir = Files.createTempDirectory("md-zip-assets-").toFile();
        File projectAssetDir = new File(tempUploadDir, "docs/" + projectCode);
        FileUtils.forceMkdir(projectAssetDir);
        FileUtils.writeByteArrayToFile(new File(projectAssetDir, "arch123.png"), "ARCH_IMG".getBytes(StandardCharsets.UTF_8));
        FileUtils.writeByteArrayToFile(new File(projectAssetDir, "flow456.jpg"), "FLOW_IMG".getBytes(StandardCharsets.UTF_8));

        try {
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode(projectCode);
        project.setProjectName("带图片项目");

        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("root");
        rootFolder.setRootFlag(true);

        ApiFolder subFolder = new ApiFolder();
        subFolder.setId(2);
        subFolder.setFolderName("docs");
        subFolder.setParentId(1);

        project.setFolders(List.of(rootFolder, subFolder));

        // 根目录文档引用图片
        ApiDocDetailVo rootDoc = new ApiDocDetailVo();
        rootDoc.setId(301);
        rootDoc.setFolderId(1);
        rootDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
        rootDoc.setDocName("README");
        rootDoc.setDocContent("# 架构图\n\n![架构](/upload/docs/img-proj/arch123.png)");

        // 子目录文档引用图片
        ApiDocDetailVo subDoc = new ApiDocDetailVo();
        subDoc.setId(302);
        subDoc.setFolderId(2);
        subDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
        subDoc.setDocName("流程说明");
        subDoc.setDocContent("# 流程\n\n<img src=\"/upload/docs/img-proj/flow456.jpg\" />");

        project.setDocs(List.of(rootDoc, subDoc));

        Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
        Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(rootDoc, subDoc));
        Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());
        Mockito.when(mockAssetStorageService.getBaseUploadPath()).thenReturn(tempUploadDir.getAbsolutePath());

        ExportDownloadVo downloadVo = new ExportDownloadVo();
        downloadVo.setType("zip");

        byte[] zipBytes = exporter.export(projectId, downloadVo);
        Assertions.assertNotNull(zipBytes);

        Map<String, String> zipContents = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(IOUtils.toByteArray(zis), StandardCharsets.UTF_8);
                zipContents.put(entry.getName(), content);
                zis.closeEntry();
            }
        }

        // 验证根目录文档相对链接为 ./assets/
        String readmeContent = zipContents.get("README.md");
        Assertions.assertTrue(readmeContent.contains("./assets/arch123.png"));

        // 验证子目录文档相对链接为 ../assets/
        String subDocContent = zipContents.get("docs/流程说明.md");
        Assertions.assertTrue(subDocContent.contains("../assets/flow456.jpg"));
        } finally {
            FileUtils.deleteDirectory(tempUploadDir);
        }
    }

    @Test
    public void testExportWithRootUploadAndCrossProjectImages() throws IOException {
        int projectId = 103;
        String projectCode = "citsgbt-api";
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode(projectCode);
        project.setProjectName("国旅运通项目");

        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("root");
        rootFolder.setRootFlag(true);

        ApiFolder pushFolder = new ApiFolder();
        pushFolder.setId(2);
        pushFolder.setFolderName("推送接口");
        pushFolder.setParentId(1);

        project.setFolders(List.of(rootFolder, pushFolder));

        // 模拟创建临时上传目录和物理文件
        File tempUploadDir = Files.createTempDirectory("test_upload_assets_").toFile();
        try {
            // 1. 模拟根目录上传图片: {uploadDir}/9b1e19eccdbc4b40893430bfd356e849.jpg
            File rootImgFile = new File(tempUploadDir, "9b1e19eccdbc4b40893430bfd356e849.jpg");
            FileUtils.writeByteArrayToFile(rootImgFile, "ROOT_IMG_BYTES".getBytes(StandardCharsets.UTF_8));

            // 2. 模拟跨项目复制图片: {uploadDir}/docs/other-proj/cross789.png
            File otherProjDir = new File(tempUploadDir, "docs/other-proj");
            FileUtils.forceMkdir(otherProjDir);
            File crossImgFile = new File(otherProjDir, "cross789.png");
            FileUtils.writeByteArrayToFile(crossImgFile, "CROSS_IMG_BYTES".getBytes(StandardCharsets.UTF_8));

            // 文档: 推送接口/消息推送接口流程.md
            ApiDocDetailVo pushDoc = new ApiDocDetailVo();
            pushDoc.setId(401);
            pushDoc.setFolderId(2);
            pushDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
            pushDoc.setDocName("消息推送接口流程");
            pushDoc.setDocContent("1. 业务流程\n\n![](/upload/9b1e19eccdbc4b40893430bfd356e849.jpg)\n\n"
                    + "2. 跨项目引用\n\n![架构](/upload/docs/other-proj/cross789.png)");

            project.setDocs(List.of(pushDoc));

            Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
            Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(pushDoc));
            Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());
            Mockito.when(mockAssetStorageService.getBaseUploadPath()).thenReturn(tempUploadDir.getAbsolutePath());

            ExportDownloadVo downloadVo = new ExportDownloadVo();
            downloadVo.setType("zip");

            byte[] zipBytes = exporter.export(projectId, downloadVo);
            Assertions.assertNotNull(zipBytes);

            Map<String, byte[]> zipEntryBytes = new HashMap<>();
            Map<String, String> zipContents = new HashMap<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    byte[] bytes = IOUtils.toByteArray(zis);
                    zipEntryBytes.put(entry.getName(), bytes);
                    if (entry.getName().endsWith(".md")) {
                        zipContents.put(entry.getName(), new String(bytes, StandardCharsets.UTF_8));
                    }
                    zis.closeEntry();
                }
            }

            // upload 根目录及其他项目目录中的图片均可作为通用资源打包
            Assertions.assertTrue(zipEntryBytes.containsKey("assets/9b1e19eccdbc4b40893430bfd356e849.jpg"));
            Assertions.assertEquals("ROOT_IMG_BYTES", new String(zipEntryBytes.get("assets/9b1e19eccdbc4b40893430bfd356e849.jpg"), StandardCharsets.UTF_8));
            Assertions.assertTrue(zipEntryBytes.containsKey("assets/cross789.png"));
            Assertions.assertEquals("CROSS_IMG_BYTES", new String(zipEntryBytes.get("assets/cross789.png"), StandardCharsets.UTF_8));

            // 验证文档内链接被成功重写为相对路径
            String docContent = zipContents.get("推送接口/消息推送接口流程.md");
            Assertions.assertNotNull(docContent);
            Assertions.assertTrue(docContent.contains("../assets/9b1e19eccdbc4b40893430bfd356e849.jpg"));
            Assertions.assertTrue(docContent.contains("../assets/cross789.png"));
            Assertions.assertFalse(docContent.contains("/upload/"));
        } finally {
            FileUtils.deleteDirectory(tempUploadDir);
        }
    }

    @Test
    public void testExportZipWithCentralizedModels() throws IOException {
        int projectId = 200;
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode("test-models-proj");
        project.setProjectName("模型集中导出项目");

        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("root");
        rootFolder.setRootFlag(true);

        ApiFolder userFolder = new ApiFolder();
        userFolder.setId(2);
        userFolder.setFolderName("user");
        userFolder.setParentId(1);

        project.setFolders(List.of(rootFolder, userFolder));

        // 根目录下接口
        ApiDocDetailVo rootApiDoc = new ApiDocDetailVo();
        rootApiDoc.setId(401);
        rootApiDoc.setFolderId(1);
        rootApiDoc.setDocType(ApiDocConstants.DOC_TYPE_API);
        rootApiDoc.setDocName("登录接口");
        rootApiDoc.setMethod("POST");
        rootApiDoc.setUrl("/api/login");

        // 子目录下接口
        ApiDocDetailVo userApiDoc = new ApiDocDetailVo();
        userApiDoc.setId(402);
        userApiDoc.setFolderId(2);
        userApiDoc.setDocType(ApiDocConstants.DOC_TYPE_API);
        userApiDoc.setDocName("获取用户");
        userApiDoc.setMethod("GET");
        userApiDoc.setUrl("/api/user");

        project.setDocs(List.of(rootApiDoc, userApiDoc));

        // 模拟 ViewGenerator 返回包含模型链接以及自身锚点的内容
        ApiDocViewGenerator viewGenerator = Mockito.mock(ApiDocViewGenerator.class);
        Mockito.when(viewGenerator.generate(any())).thenAnswer(invocation -> {
            MdViewContext ctx = invocation.getArgument(0);
            if (ctx.getSchemasMap() != null) {
                io.swagger.v3.oas.models.media.Schema<Object> userSchema = new io.swagger.v3.oas.models.media.Schema<>();
                userSchema.setName("UserVO");
                userSchema.setDescription("用户信息模型");
                ctx.getSchemasMap().put("UserVO", userSchema);

                io.swagger.v3.oas.models.media.Schema<Object> loginSchema = new io.swagger.v3.oas.models.media.Schema<>();
                loginSchema.setName("LoginDTO");
                loginSchema.setDescription("登录参数模型");
                ctx.getSchemasMap().put("LoginDTO", loginSchema);
            }
            if ("/api/login".equals(ctx.getApiDocDetail().getUrl())) {
                return "### 基本信息\n\n- [基本信息](#基本信息)\n- 参数模型：<a href=\"#LoginDTO\">LoginDTO</a>\n- 备用链接：[LoginDTO](#LoginDTO)";
            } else {
                return "### 基本信息\n\n- [基本信息](#基本信息)\n- 响应模型：<a href=\"#UserVO\">UserVO</a>\n- 备用链接：[UserVO](#UserVO)";
            }
        });
        Mockito.when(viewGenerator.sortSchemasMap(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.setField(exporter, "apiDocViewGenerator", viewGenerator);

        Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
        Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(rootApiDoc, userApiDoc));
        Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());

        ExportDownloadVo downloadVo = new ExportDownloadVo();
        downloadVo.setType("zip");

        byte[] zipBytes = exporter.export(projectId, downloadVo);
        Assertions.assertNotNull(zipBytes);

        Map<String, String> zipContents = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".md")) {
                    zipContents.put(entry.getName(), new String(IOUtils.toByteArray(zis), StandardCharsets.UTF_8));
                }
                zis.closeEntry();
            }
        }

        // 1. 验证生成了独立的 models/models.md 文件
        Assertions.assertTrue(zipContents.containsKey("models/models.md"));
        String modelsContent = zipContents.get("models/models.md");
        Assertions.assertFalse(modelsContent.contains("<a id="));
        Assertions.assertTrue(modelsContent.contains("## UserVO"));
        Assertions.assertTrue(modelsContent.contains("用户信息模型"));
        Assertions.assertTrue(modelsContent.contains("## LoginDTO"));
        Assertions.assertTrue(modelsContent.contains("登录参数模型"));

        // 2. 验证根目录下文档（登录接口）的模型链接被重写为 ./models/models.md#Anchor，但内部自身锚点 #基本信息 保持不变
        String rootDocContent = zipContents.get("登录接口.md");
        Assertions.assertNotNull(rootDocContent);
        Assertions.assertTrue(rootDocContent.contains("href=\"./models/models.md#LoginDTO\""));
        Assertions.assertTrue(rootDocContent.contains("[LoginDTO](./models/models.md#LoginDTO)"));
        Assertions.assertTrue(rootDocContent.contains("[基本信息](#基本信息)"));

        // 3. 验证子目录下文档（user/获取用户.md）的模型链接被重写为 ../models/models.md#Anchor，自身锚点保持不变
        String userDocContent = zipContents.get("user/获取用户.md");
        Assertions.assertNotNull(userDocContent);
        Assertions.assertTrue(userDocContent.contains("href=\"../models/models.md#UserVO\""));
        Assertions.assertTrue(userDocContent.contains("[UserVO](../models/models.md#UserVO)"));
        Assertions.assertTrue(userDocContent.contains("[基本信息](#基本信息)"));
    }
}
