package com.fugary.simple.api.exports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.exports.openapi.OpenApiApiDocExporterImpl;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import com.fugary.simple.api.web.vo.query.ProjectDetailQueryVo;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class OpenApiApiDocExporterTest {

    private OpenApiApiDocExporterImpl exporter;
    private ApiProjectService mockProjectService;
    private ApiProjectInfoDetailService mockProjectInfoDetailService;
    private ApiFolderService mockFolderService;

    @BeforeEach
    public void setup() {
        exporter = new OpenApiApiDocExporterImpl();
        mockProjectService = Mockito.mock(ApiProjectService.class);
        mockProjectInfoDetailService = Mockito.mock(ApiProjectInfoDetailService.class);
        mockFolderService = Mockito.mock(ApiFolderService.class);

        ReflectionTestUtils.setField(exporter, "apiProjectService", mockProjectService);
        ReflectionTestUtils.setField(exporter, "apiProjectInfoDetailService", mockProjectInfoDetailService);
        ReflectionTestUtils.setField(exporter, "apiFolderService", mockFolderService);
    }

    @Test
    public void testOpenApiExportTreeAndPathOrder() {
        int projectId = 1;
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(projectId);
        project.setProjectCode("test-openapi");
        project.setProjectName("OpenAPI 保序测试");
        project.setApiVersion("1.0.0");

        // 根目录
        ApiFolder rootFolder = new ApiFolder();
        rootFolder.setId(1);
        rootFolder.setFolderName("Root");
        rootFolder.setRootFlag(true);
        rootFolder.setSortId(0);

        // 目录 1: 账号认证 (sortId = 10)
        ApiFolder authFolder = new ApiFolder();
        authFolder.setId(2);
        authFolder.setFolderName("账号认证");
        authFolder.setParentId(1);
        authFolder.setSortId(10);

        // 目录 2: 用户管理 (sortId = 20)
        ApiFolder userFolder = new ApiFolder();
        userFolder.setId(3);
        userFolder.setFolderName("用户管理");
        userFolder.setParentId(1);
        userFolder.setSortId(20);

        // 子目录: 用户管理 / 角色权限 (sortId = 10)
        ApiFolder roleFolder = new ApiFolder();
        roleFolder.setId(4);
        roleFolder.setFolderName("角色权限");
        roleFolder.setParentId(3);
        roleFolder.setSortId(10);

        List<ApiFolder> folders = List.of(rootFolder, authFolder, userFolder, roleFolder);
        project.setFolders(folders);

        // 接口定义
        ApiDocDetailVo docLogin = new ApiDocDetailVo();
        docLogin.setId(101);
        docLogin.setFolderId(2);
        docLogin.setDocType(ApiDocConstants.DOC_TYPE_API);
        docLogin.setDocName("登录接口");
        docLogin.setUrl("/api/v1/auth/login");
        docLogin.setMethod("POST");
        docLogin.setSortId(10);

        ApiDocDetailVo docUserList = new ApiDocDetailVo();
        docUserList.setId(102);
        docUserList.setFolderId(3);
        docUserList.setDocType(ApiDocConstants.DOC_TYPE_API);
        docUserList.setDocName("用户列表");
        docUserList.setUrl("/api/v1/users");
        docUserList.setMethod("GET");
        docUserList.setSortId(10);

        ApiDocDetailVo docCreateUser = new ApiDocDetailVo();
        docCreateUser.setId(103);
        docCreateUser.setFolderId(3);
        docCreateUser.setDocType(ApiDocConstants.DOC_TYPE_API);
        docCreateUser.setDocName("创建用户");
        docCreateUser.setUrl("/api/v1/users"); // 与 docUserList 相同 URL 不同方法
        docCreateUser.setMethod("POST");
        docCreateUser.setSortId(20);

        ApiDocDetailVo docRoleList = new ApiDocDetailVo();
        docRoleList.setId(104);
        docRoleList.setFolderId(4);
        docRoleList.setDocType(ApiDocConstants.DOC_TYPE_API);
        docRoleList.setDocName("角色列表");
        docRoleList.setUrl("/api/v1/roles");
        docRoleList.setMethod("GET");
        docRoleList.setSortId(10);

        project.setDocs(List.of(docRoleList, docCreateUser, docUserList, docLogin)); // 乱序输入

        Mockito.when(mockProjectService.loadProjectVo(any(ProjectDetailQueryVo.class))).thenReturn(project);
        Mockito.when(mockProjectInfoDetailService.loadDetailList(any())).thenReturn(List.of(docRoleList, docCreateUser, docUserList, docLogin));
        Mockito.when(mockProjectInfoDetailService.loadByProject(eq(projectId), any())).thenReturn(Collections.emptyList());
        Mockito.when(mockFolderService.calcFolderMap(any())).thenReturn(Pair.of(Map.of(), Map.of(1, "Root", 2, "Root/auth", 3, "Root/user", 4, "Root/user/role")));
        Mockito.when(mockFolderService.calcFolderNameMap(any())).thenReturn(Map.of(1, "Root", 2, "Root/账号认证", 3, "Root/用户管理", 4, "Root/用户管理/角色权限"));

        ExportDownloadVo downloadVo = new ExportDownloadVo();
        downloadVo.setType("json");

        OpenAPI openAPI = exporter.export(projectId, downloadVo);
        Assertions.assertNotNull(openAPI);

        // 验证 Tags 顺序: 账号认证 -> 用户管理 -> 角色权限
        List<String> tagNames = openAPI.getTags().stream().map(Tag::getName).collect(Collectors.toList());
        Assertions.assertEquals(List.of("账号认证", "用户管理", "角色权限"), tagNames);

        // 验证 Paths 顺序: /api/v1/auth/login -> /api/v1/users -> /api/v1/roles
        List<String> paths = new ArrayList<>(openAPI.getPaths().keySet());
        Assertions.assertEquals(List.of("/api/v1/auth/login", "/api/v1/users", "/api/v1/roles"), paths);

        // 验证同一 URL 下的方法聚合 (GET 与 POST)
        Assertions.assertNotNull(openAPI.getPaths().get("/api/v1/users").getGet());
        Assertions.assertNotNull(openAPI.getPaths().get("/api/v1/users").getPost());
        Assertions.assertEquals("用户列表", openAPI.getPaths().get("/api/v1/users").getGet().getSummary());
        Assertions.assertEquals("创建用户", openAPI.getPaths().get("/api/v1/users").getPost().getSummary());
    }
}
