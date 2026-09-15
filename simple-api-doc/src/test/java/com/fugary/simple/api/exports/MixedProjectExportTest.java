package com.fugary.simple.api.exports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.entity.api.ApiProjectInfoDetail;
import com.fugary.simple.api.exports.md.*;
import com.fugary.simple.api.exports.openapi.OpenApiApiDocExporterImpl;
import com.fugary.simple.api.service.apidoc.ApiFolderService;
import com.fugary.simple.api.service.apidoc.ApiProjectInfoDetailService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.impl.apidoc.ApiProjectInfoDetailServiceImpl;
import com.fugary.simple.api.utils.SchemaJsonUtils;
import com.fugary.simple.api.utils.SchemaYamlUtils;
import com.fugary.simple.api.web.vo.exports.ExportDownloadVo;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import com.fugary.simple.api.web.vo.project.ApiProjectDetailVo;
import freemarker.template.Configuration;
import freemarker.template.TemplateMethodModelEx;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MixedProjectExportTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"markdown", "V30", "V31", "new-project"})
    void exportsMixedProjectWithActualMarkdownRenderer(String specVersion) throws Exception {
        ApiFolder root = new ApiFolder();
        root.setId(1);
        root.setRootFlag(true);
        root.setFolderName("Root");
        ApiProjectInfo info = new ApiProjectInfo();
        info.setId(10);
        info.setSpecVersion(specVersion);
        info.setVersion("1.0.0");
        ApiDocDetailVo api = new ApiDocDetailVo();
        api.setId(1);
        api.setInfoId(10);
        api.setFolderId(1);
        api.setDocType(ApiDocConstants.DOC_TYPE_API);
        api.setDocName("Test API");
        api.setMethod("GET");
        api.setUrl("/test");
        ApiProjectInfoDetail response = new ApiProjectInfoDetail();
        response.setSchemaName("200");
        response.setStatusCode(200);
        response.setContentType("application/json");
        response.setSchemaContent("{\"schema\":{\"type\":\"object\",\"properties\":{\"inlineField\":{\"type\":\"string\"}}}}");
        api.setResponsesSchemas(List.of(response));
        ApiDocDetailVo md = new ApiDocDetailVo();
        md.setId(2);
        md.setInfoId(10);
        md.setFolderId(1);
        md.setDocType(ApiDocConstants.DOC_TYPE_MD);
        md.setDocKey("guide.md");
        md.setDocName("Guide");
        md.setDocContent("# Original Markdown");
        ApiDocDetailVo secondApi = new ApiDocDetailVo();
        secondApi.setId(3);
        secondApi.setInfoId(10);
        secondApi.setFolderId(1);
        secondApi.setDocType(ApiDocConstants.DOC_TYPE_API);
        secondApi.setDocName("Second API");
        secondApi.setMethod("GET");
        secondApi.setUrl("/second");
        ApiProjectInfoDetail secondResponse = new ApiProjectInfoDetail();
        secondResponse.setSchemaName("200");
        secondResponse.setStatusCode(200);
        secondResponse.setContentType("application/json");
        secondResponse.setSchemaContent(response.getSchemaContent().replace("inlineField", "secondField"));
        secondApi.setResponsesSchemas(List.of(secondResponse));
        ApiProjectDetailVo project = new ApiProjectDetailVo();
        project.setId(1);
        project.setProjectName("Mixed project");
        project.setFolders(List.of(root));
        project.setInfoList(List.of(info));
        project.setDocs(List.of(api, md, secondApi));
        if ("new-project".equals(specVersion)) {
            project.setInfoList(List.of());
            api.setInfoId(null);
            md.setInfoId(null);
            secondApi.setInfoId(null);
            specVersion = null;
        }

        ApiProjectService projects = mock(ApiProjectService.class);
        when(projects.loadProjectVo(any())).thenReturn(project);
        ApiProjectInfoDetailService details = spy(new ApiProjectInfoDetailServiceImpl());
        doReturn(List.of(api, md, secondApi)).when(details).loadDetailList(any());
        doReturn(List.of()).when(details).loadByProject(any(), any());
        ApiFolderService folders = mock(ApiFolderService.class);
        when(folders.calcFolderMap(any())).thenReturn(Pair.of(Map.of(), Map.of(1, "Root")));
        when(folders.calcFolderNameMap(any())).thenReturn(Map.of(1, "Root"));

        OpenApiApiDocExporterImpl openApiExporter = new OpenApiApiDocExporterImpl();
        ReflectionTestUtils.setField(openApiExporter, "apiProjectService", projects);
        ReflectionTestUtils.setField(openApiExporter, "apiProjectInfoDetailService", details);
        ReflectionTestUtils.setField(openApiExporter, "apiFolderService", folders);
        ExportDownloadVo filter = new ExportDownloadVo();
        OpenAPI openAPI = openApiExporter.export(1, filter);
        assertEquals("V30".equals(specVersion) ? "3.0.1" : "3.1.0", openAPI.getOpenapi());
        assertEquals("V30".equals(specVersion) ? SpecVersion.V30 : SpecVersion.V31, openAPI.getSpecVersion());
        assertNotNull(openAPI.getPaths().get("/test").getGet());
        assertTrue(openAPI.getExtensions().containsKey(ApiDocConstants.X_SIMPLE_MARKDOWN_FILES));
        assertTrue(SchemaJsonUtils.toJson(openAPI, SchemaJsonUtils.isV31(openAPI)).contains("/test"));
        assertTrue(SchemaYamlUtils.toYaml(openAPI, SchemaJsonUtils.isV31(openAPI)).contains("/test"));

        if ("V30".equals(specVersion) || "V31".equals(specVersion)) {
            String importedVersion = "V30".equals(specVersion) ? "3.0.3" : "3.1.1";
            info.setOasVersion(importedVersion);
            assertEquals(importedVersion, openApiExporter.export(1, filter).getOpenapi());
        }

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassForTemplateLoading(getClass(), "/templates");
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ApiDocFreemarkerUtils utils = new ApiDocFreemarkerUtils();
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        utils.setMessageSource(messages);
        configuration.setSharedVariable("utils", utils);
        configuration.setSharedVariable("message", (TemplateMethodModelEx) arguments -> arguments.get(0).toString());
        MarkdownApiDocViewGeneratorImpl generator = new MarkdownApiDocViewGeneratorImpl();
        generator.setFreemarkerConfig(configuration);
        generator.setApiDocFreemarkerUtils(utils);

        MarkdownApiDocExporterImpl markdownExporter = new MarkdownApiDocExporterImpl();
        markdownExporter.setApiProjectService(projects);
        markdownExporter.setApiProjectInfoDetailService(details);
        markdownExporter.setApiDocViewGenerator(generator);
        markdownExporter.setFreemarkerConfig(configuration);
        String markdown = markdownExporter.export(1, filter);
        assertTrue(markdown.contains("/test"));
        assertTrue(markdown.contains(md.getDocContent()));
        assertTrue(markdown.contains("**`inlineField`**"));
        assertTrue(markdown.contains("## _response_200"));
        assertTrue(markdown.contains("**`secondField`**"));
        assertTrue(markdown.contains("## _response_200_2"));

        MarkdownZipApiDocExporterImpl zipExporter = new MarkdownZipApiDocExporterImpl();
        zipExporter.setApiProjectService(projects);
        zipExporter.setApiProjectInfoDetailService(details);
        zipExporter.setApiDocViewGenerator(generator);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipExporter.export(1, filter)))) {
            boolean foundApi = false;
            boolean foundSecondApi = false;
            boolean foundMd = false;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                foundApi |= entry.getName().equals("Test API.md") && content.contains("/test")
                        && content.contains("#### _response_200") && content.contains("**`inlineField`**")
                        && !content.contains("**`secondField`**");
                foundSecondApi |= entry.getName().equals("Second API.md") && content.contains("/second")
                        && content.contains("#### _response_200") && content.contains("**`secondField`**")
                        && !content.contains("**`inlineField`**");
                foundMd |= entry.getName().equals("Guide.md") && content.contains(md.getDocContent());
            }
            assertTrue(foundApi);
            assertTrue(foundSecondApi);
            assertTrue(foundMd);
        }
    }
}
