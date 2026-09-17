package com.fugary.simple.api.exports.md;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.exports.ApiDocViewGenerator;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectInfoDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import freemarker.template.Configuration;
import freemarker.template.TemplateMethodModelEx;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class MarkdownApiDocExporterTest {

    private MarkdownApiDocExporterImpl exporter;
    private ApiProjectService mockProjectService;
    private ApiProjectInfoDetailService mockProjectInfoDetailService;
    private DocAssetStorageService mockAssetStorageService;

    @BeforeEach
    public void setup() throws freemarker.template.TemplateModelException {
        exporter = new MarkdownApiDocExporterImpl();

        mockProjectService = Mockito.mock(ApiProjectService.class);
        mockProjectInfoDetailService = Mockito.mock(ApiProjectInfoDetailService.class);
        mockAssetStorageService = Mockito.spy(new com.fugary.simple.api.service.impl.apidoc.asset.DocAssetStorageServiceImpl());

        ApiDocViewGenerator mockViewGenerator = Mockito.mock(ApiDocViewGenerator.class);
        Mockito.when(mockViewGenerator.generate(any())).thenReturn("### 基本信息\n\n* **请求方式**: GET\n\n### 请求参数\n\n无参数");

        ApiDocFreemarkerUtils utils = new ApiDocFreemarkerUtils();
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setUseCodeAsDefaultMessage(true);
        utils.setMessageSource(messages);

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassForTemplateLoading(getClass(), "/templates");
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setSharedVariable("utils", utils);
        configuration.setSharedVariable("message", (TemplateMethodModelEx) arguments -> arguments.get(0).toString());

        ReflectionTestUtils.setField(exporter, "apiProjectService", mockProjectService);
        ReflectionTestUtils.setField(exporter, "apiProjectInfoDetailService", mockProjectInfoDetailService);
        ReflectionTestUtils.setField(exporter, "apiDocViewGenerator", mockViewGenerator);
        ReflectionTestUtils.setField(exporter, "docAssetStorageService", mockAssetStorageService);
        ReflectionTestUtils.setField(exporter, "freemarkerConfig", configuration);
    }

    @Test
    public void testExportSingleMarkdownWithMixedDocsAndApis() throws IOException {
        int projectId = 100;
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode("test-proj");
        project.setProjectName("测试系统");
        project.setApiVersion("1.0.0");
        project.setDescription("这是测试系统的单MD导出文档");

        // 根文件夹与子文件夹
        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("root");
        rootFolder.setRootFlag(true);

        ApiFolder userFolder = new ApiFolder();
        userFolder.setId(2);
        userFolder.setFolderName("用户中心");
        userFolder.setParentId(1);
        userFolder.setSortId(10);

        project.setFolders(List.of(rootFolder, userFolder));

        // 根目录下的说明文档
        ApiDocDetailVo rootReadme = new ApiDocDetailVo();
        rootReadme.setId(101);
        rootReadme.setFolderId(1);
        rootReadme.setDocType(ApiDocConstants.DOC_TYPE_MD);
        rootReadme.setDocName("README");
        rootReadme.setSortId(1);
        rootReadme.setDocContent("# 测试系统\n\n欢迎使用本系统。\n\n请参考 [权限设计说明](./auth.md)");

        // 在同一文件夹（用户中心）下，API 的 sortId 较小 (10)，MD 文档的 sortId 较大 (50)
        // 期望在单 MD 导出中：MD 文档优先置顶（先看架构/说明），API 紧随其后
        ApiDocDetailVo userListApi = new ApiDocDetailVo();
        userListApi.setId(102);
        userListApi.setFolderId(2);
        userListApi.setDocType(ApiDocConstants.DOC_TYPE_API);
        userListApi.setDocName("用户列表接口");
        userListApi.setUrl("/api/v1/users");
        userListApi.setMethod("GET");
        userListApi.setSortId(10);

        // 模拟本地图片
        File tempImg = Files.createTempFile("test_img", ".png").toFile();
        FileUtils.writeByteArrayToFile(tempImg, new byte[]{1, 2, 3, 4, 5});
        Mockito.when(mockAssetStorageService.resolveImageFile(any(), eq("arch.png"), eq("test-proj"))).thenReturn(tempImg);

        ApiDocDetailVo userAuthDoc = new ApiDocDetailVo();
        userAuthDoc.setId(103);
        userAuthDoc.setFolderId(2);
        userAuthDoc.setDocType(ApiDocConstants.DOC_TYPE_MD);
        userAuthDoc.setDocName("权限设计说明");
        userAuthDoc.setUrl("auth.md");
        userAuthDoc.setSortId(50);
        userAuthDoc.setDocContent("# 权限设计说明\n\n架构设计如下图：\n\n![架构图](/upload/docs/test-proj/arch.png)\n\n## 1. 认证机制\n\n### 1.1 Token\n\nJWT Token。");

        ApiDocDetailVo rootPingApi = new ApiDocDetailVo();
        rootPingApi.setId(104);
        rootPingApi.setFolderId(1);
        rootPingApi.setDocType(ApiDocConstants.DOC_TYPE_API);
        rootPingApi.setDocName("健康检查");
        rootPingApi.setUrl("/ping");
        rootPingApi.setMethod("GET");
        rootPingApi.setSortId(20);

        project.setDocs(List.of(rootReadme, rootPingApi, userListApi, userAuthDoc));

        ApiProjectInfoDetailVo projectInfo = new ApiProjectInfoDetailVo();
        projectInfo.setVersion("1.0.0");
        Mockito.when(mockProjectInfoDetailService.mergeInfoDetailVo(any())).thenReturn(projectInfo);
        Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
        Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(rootReadme, rootPingApi, userListApi, userAuthDoc));
        Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());

        ExportDownloadVo downloadVo = new ExportDownloadVo();
        downloadVo.setType("md");

        String exportedMd = exporter.export(projectId, downloadVo);
        Assertions.assertNotNull(exportedMd);

        // 1. 验证标题层级与多级目录
        Assertions.assertTrue(exportedMd.contains("# 测试系统"));
        Assertions.assertTrue(exportedMd.contains("## 📁 用户中心"));
        Assertions.assertTrue(exportedMd.contains("## 📄 README"));
        Assertions.assertTrue(exportedMd.contains("## 🔗 [GET] 健康检查 (`/ping`)"));
        Assertions.assertTrue(exportedMd.contains("### 📄 权限设计说明"));
        Assertions.assertTrue(exportedMd.contains("### 🔗 [GET] 用户列表接口 (`/api/v1/users`)"));
        Assertions.assertFalse(exportedMd.contains("<a id="));
        Assertions.assertFalse(exportedMd.contains("## 接口地址"));

        // 根目录下说明文档 101（README）优先排在 根接口 104（健康检查）之前
        int rootReadmeIdx = exportedMd.indexOf("## 📄 README");
        int rootPingIdx = exportedMd.indexOf("## 🔗 [GET] 健康检查");
        Assertions.assertTrue(rootReadmeIdx > 0 && rootPingIdx > 0);
        Assertions.assertTrue(rootReadmeIdx < rootPingIdx, "根目录下说明文档必须排在根目录接口之前");

        // 2. 验证排序：在用户中心下，说明文档 103（权限设计说明）应当排在 接口 102（用户列表接口）之前！
        int authDocIdx = exportedMd.indexOf("### 📄 权限设计说明");
        int apiDocIdx = exportedMd.indexOf("### 🔗 [GET] 用户列表接口");
        Assertions.assertTrue(authDocIdx > 0 && apiDocIdx > 0, "应同时包含说明文档和接口");
        Assertions.assertTrue(authDocIdx < apiDocIdx, "同一文件夹下，说明文档必须排在 API 接口之前！authDocIdx=" + authDocIdx + ", apiDocIdx=" + apiDocIdx);

        // 3. 验证正文标题降级（首行与 docName 重复的 # 权限设计说明 已被去除，## 1. 认证机制 降级为 ####，### 1.1 Token 降级为 #####）
        Assertions.assertTrue(exportedMd.contains("#### 1. 认证机制"));
        Assertions.assertTrue(exportedMd.contains("##### 1.1 Token"));
        // 验证接口内部标题降级：用户中心下的接口 ### 基本信息 降级为 #### 基本信息
        Assertions.assertTrue(exportedMd.contains("#### 基本信息"));

        // 4. 验证默认情况下不嵌入图片 Base64，保留原始图片链接
        Assertions.assertTrue(exportedMd.contains("![架构图](/upload/docs/test-proj/arch.png)"));
        Assertions.assertFalse(exportedMd.contains("data:image/png;base64"));

        // 验证开启 embedImages 时，内联图片为 Base64
        userAuthDoc.setDocContent("# 权限设计说明\n\n架构设计如下图：\n\n![架构图](/upload/docs/test-proj/arch.png)\n\n## 1. 认证机制\n\n### 1.1 Token\n\nJWT Token。");
        downloadVo.setEmbedImages(true);
        String exportedMdWithEmbed = exporter.export(projectId, downloadVo);
        Assertions.assertTrue(exportedMdWithEmbed.contains("![架构图](data:image/png;base64,"));

        // 5. 验证跨文档相对链接已转换为单文档内部锚点
        Assertions.assertTrue(exportedMd.contains("[权限设计说明](#权限设计说明)"));

        // 清理临时文件
        FileUtils.deleteQuietly(tempImg);
    }
}
