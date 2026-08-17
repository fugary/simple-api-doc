package com.fugary.simple.api.imports;

import com.fugary.simple.api.imports.swagger.SwaggerImporterImpl;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        Assertions.assertFalse(importer.match(null));
        Assertions.assertFalse(importer.match("   "));
    }
}
