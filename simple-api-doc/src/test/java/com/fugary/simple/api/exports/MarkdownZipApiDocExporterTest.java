package com.fugary.simple.api.exports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.exports.md.MarkdownZipApiDocExporterImpl;
import com.fugary.simple.api.exports.md.MarkdownApiDocViewGeneratorImpl;
import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

        ReflectionTestUtils.setField(exporter, "apiProjectService", mockProjectService);
        ReflectionTestUtils.setField(exporter, "apiProjectInfoDetailService", mockProjectInfoDetailService);
        ReflectionTestUtils.setField(exporter, "apiDocViewGenerator", mockViewGenerator);
        ReflectionTestUtils.setField(exporter, "docAssetStorageService", mockAssetStorageService);
    }

    @Test
    public void testExportMultiLevelZip() throws IOException {
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
        Mockito.when(mockAssetStorageService.getBaseUploadPath()).thenReturn(System.getProperty("java.io.tmpdir"));

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
    }
}
