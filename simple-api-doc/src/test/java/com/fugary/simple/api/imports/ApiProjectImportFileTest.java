package com.fugary.simple.api.imports;

import com.fugary.simple.api.imports.swagger.SwaggerImporterImpl;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ApiProjectImportFileTest {

    @Test
    public void testSupportedImportFile() {
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.json"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.JSON"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.yaml"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.YAML"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.yml"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.YML"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("openapi.v3.json"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.md"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.MD"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.markdown"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.zip"));
        Assertions.assertTrue(SimpleModelUtils.isSupportedImportFile("test.ZIP"));

        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.xlsx"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.xls"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.csv"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.txt"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile("test.docx"));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile(""));
        Assertions.assertFalse(SimpleModelUtils.isSupportedImportFile(null));
    }

    @Test
    public void testSwaggerImporterMatch() {
        SwaggerImporterImpl importer = new SwaggerImporterImpl();

        // 1. Valid OpenAPI 3 JSON
        String openapiJson = "{\n  \"openapi\": \"3.0.1\",\n  \"info\": {\"title\": \"Test API\"}\n}";
        Assertions.assertTrue(importer.match(openapiJson));

        // 2. Valid Swagger 2.0 JSON
        String swaggerJson = "{\n  \"swagger\": \"2.0\",\n  \"info\": {\"title\": \"Test API\"}\n}";
        Assertions.assertTrue(importer.match(swaggerJson));

        // 3. Valid Struct JSON with paths and info
        String structJson = "{\n  \"info\": {\"title\": \"Test\"},\n  \"paths\": {}\n}";
        Assertions.assertTrue(importer.match(structJson));

        // 4. Valid OpenAPI 3 YAML
        String openapiYaml = "openapi: 3.0.0\ninfo:\n  title: Sample API\npaths:\n  /users:\n    get:\n      summary: Get users";
        Assertions.assertTrue(importer.match(openapiYaml));

        // 5. Valid Swagger 2.0 YAML
        String swaggerYaml = "swagger: '2.0'\ninfo:\n  title: Sample API\npaths:\n  /users:\n    get:\n      summary: Get users";
        Assertions.assertTrue(importer.match(swaggerYaml));

        // 6. Valid Struct YAML with paths: and info:
        String structYaml = "info:\n  title: Sample API\npaths:\n  /users:\n    get:\n      summary: Get users";
        Assertions.assertTrue(importer.match(structYaml));

        // 7. Invalid Fastmock JSON (array format or mock format)
        String fastmockArrayJson = "[{\"id\": 1, \"url\": \"/api/test\", \"mockRule\": \"{}\"}]";
        Assertions.assertFalse(importer.match(fastmockArrayJson));

        String fastmockObjJson = "{\"code\": \"0000\", \"data\": {\"name\": \"fastmock\"}}";
        Assertions.assertFalse(importer.match(fastmockObjJson));

        // 8. Invalid general JSON (e.g. package.json)
        String packageJson = "{\n  \"name\": \"simple-api-doc\",\n  \"version\": \"1.0.0\",\n  \"dependencies\": {}\n}";
        Assertions.assertFalse(importer.match(packageJson));

        // 9. Invalid Docker Compose YAML
        String dockerComposeYaml = "version: '3.8'\nservices:\n  web:\n    image: nginx:alpine\n    ports:\n      - '80:80'";
        Assertions.assertFalse(importer.match(dockerComposeYaml));

        // 10. Null / Empty
        Assertions.assertFalse(importer.match(""));
        Assertions.assertFalse(importer.match((String) null));
        Assertions.assertFalse(importer.match((com.fugary.simple.api.web.vo.imports.DocSourceData) null));
        Assertions.assertFalse(importer.match("   "));
    }

    @Test
    public void testSwaggerImporterDeprecated() {
        SwaggerImporterImpl importer = new SwaggerImporterImpl();
        String openapiYaml = "openapi: 3.0.0\n" +
                "info:\n" +
                "  title: Sample API\n" +
                "  version: 1.0.0\n" +
                "paths:\n" +
                "  /users:\n" +
                "    get:\n" +
                "      summary: Get users (deprecated)\n" +
                "      deprecated: true\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: OK\n" +
                "    post:\n" +
                "      summary: Create user\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: OK\n";
        ExportApiProjectVo projectVo = importer.doImport(openapiYaml);
        Assertions.assertNotNull(projectVo);
        List<ExportApiDocVo> docs = projectVo.getDocs();
        if (docs.isEmpty() && !projectVo.getFolders().isEmpty()) {
            docs = projectVo.getFolders().get(0).getDocs();
        }
        Assertions.assertFalse(docs.isEmpty());
        ExportApiDocVo getDoc = docs.stream().filter(d -> "GET".equalsIgnoreCase(d.getMethod())).findFirst().orElse(null);
        ExportApiDocVo postDoc = docs.stream().filter(d -> "POST".equalsIgnoreCase(d.getMethod())).findFirst().orElse(null);

        Assertions.assertNotNull(getDoc);
        Assertions.assertTrue(Boolean.TRUE.equals(getDoc.getDeprecated()));

        Assertions.assertNotNull(postDoc);
        Assertions.assertNull(postDoc.getDeprecated());
    }

    @Test
    public void testSwaggerImporterFolderCodeAndName() {
        SwaggerImporterImpl importer = new SwaggerImporterImpl();
        String openapiYaml = "openapi: 3.0.0\n" +
                "info:\n" +
                "  title: Sample API\n" +
                "  version: 1.0.0\n" +
                "paths:\n" +
                "  /users:\n" +
                "    get:\n" +
                "      summary: Get users\n" +
                "      tags:\n" +
                "        - 用户中心\n" +
                "      x-simple-folder: 系统管理/用户中心\n" +
                "      x-simple-folder-code: system/user-api\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: OK\n";
        ExportApiProjectVo projectVo = importer.doImport(openapiYaml);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(1, projectVo.getFolders().size());
        var topFolder = projectVo.getFolders().get(0);
        Assertions.assertEquals("系统管理", topFolder.getFolderName());
        Assertions.assertEquals("system", topFolder.getFolderCode());
        Assertions.assertEquals(1, topFolder.getFolders().size());

        var childFolder = topFolder.getFolders().get(0);
        Assertions.assertEquals("用户中心", childFolder.getFolderName());
        Assertions.assertEquals("user-api", childFolder.getFolderCode());
        Assertions.assertEquals(1, childFolder.getDocs().size());
    }

    @Test
    public void testSwaggerImporterTagsOrder() {
        SwaggerImporterImpl importer = new SwaggerImporterImpl();
        // paths 中 profile 先于 auth，但 tags 声明中 auth 先于 profile
        String openapiYaml = "openapi: 3.0.0\n" +
                "info:\n" +
                "  title: Sample API\n" +
                "  version: 1.0.0\n" +
                "tags:\n" +
                "  - name: 账号认证\n" +
                "    description: 认证相关接口\n" +
                "  - name: Profile\n" +
                "    description: 个人信息相关\n" +
                "  - name: TaProduct\n" +
                "    description: 产品相关\n" +
                "paths:\n" +
                "  /api/v3/profile/user:\n" +
                "    get:\n" +
                "      summary: Get Profile\n" +
                "      tags:\n" +
                "        - Profile\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: OK\n" +
                "  /api/v3/auth/login:\n" +
                "    post:\n" +
                "      summary: Login\n" +
                "      tags:\n" +
                "        - 账号认证\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: OK\n" +
                "  /api/v3/product/list:\n" +
                "    get:\n" +
                "      summary: Product List\n" +
                "      tags:\n" +
                "        - TaProduct\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: OK\n";
        ExportApiProjectVo projectVo = importer.doImport(openapiYaml);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(3, projectVo.getFolders().size());
        // 验证顶层目录顺序必须严格遵循 tags 声明顺序: 账号认证 -> Profile -> TaProduct
        Assertions.assertEquals("账号认证", projectVo.getFolders().get(0).getFolderName());
        Assertions.assertEquals("Profile", projectVo.getFolders().get(1).getFolderName());
        Assertions.assertEquals("TaProduct", projectVo.getFolders().get(2).getFolderName());
    }
}
