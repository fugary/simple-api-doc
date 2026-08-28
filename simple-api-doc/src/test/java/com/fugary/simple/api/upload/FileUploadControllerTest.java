package com.fugary.simple.api.upload;

import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.web.controllers.upload.FileUploadController;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUploadControllerTest {

    private FileUploadController controller;
    private Path tempUploadDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempUploadDir = Files.createTempDirectory("simple_api_doc_upload_test");
        controller = new FileUploadController();

        DocAssetStorageService mockStorage = Mockito.mock(DocAssetStorageService.class);
        Mockito.when(mockStorage.getBaseUploadPath()).thenReturn(tempUploadDir.toString());
        ReflectionTestUtils.setField(controller, "docAssetStorageService", mockStorage);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempUploadDir != null && Files.exists(tempUploadDir)) {
            FileUtils.deleteDirectory(tempUploadDir.toFile());
        }
    }

    @Test
    public void testShowFileFlatPath() throws IOException {
        // 1. 兼容原先的单层上传文件路径：/upload/test-uuid.png
        File testFile = new File(tempUploadDir.toFile(), "test-uuid.png");
        FileUtils.writeByteArrayToFile(testFile, "PNG_MOCK_BYTES".getBytes(StandardCharsets.UTF_8));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/upload/test-uuid.png");
        request.setContextPath("");

        ResponseEntity<InputStreamResource> response = controller.showFile(request);
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        Assertions.assertNotNull(response.getBody());
        try (var is = response.getBody().getInputStream()) {
            Assertions.assertTrue(is.available() > 0);
        }
    }

    @Test
    public void testShowFileMultiLevelSubfolderPath() throws IOException {
        // 2. 支持多级子目录：/upload/docs/my-test/18b5f4feca13f32fb7bfe9436d6748c0.png
        File subDir = new File(tempUploadDir.toFile(), "docs/my-test");
        FileUtils.forceMkdir(subDir);
        File testFile = new File(subDir, "18b5f4feca13f32fb7bfe9436d6748c0.png");
        FileUtils.writeByteArrayToFile(testFile, "PNG_BYTES".getBytes(StandardCharsets.UTF_8));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/upload/docs/my-test/18b5f4feca13f32fb7bfe9436d6748c0.png");
        request.setContextPath("");

        ResponseEntity<InputStreamResource> response = controller.showFile(request);
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        Assertions.assertNotNull(response.getBody());
        try (var is = response.getBody().getInputStream()) {
            Assertions.assertTrue(is.available() > 0);
        }
    }

    @Test
    public void testShowFileNotFound() throws IOException {
        // 3. 不存在的文件返回 404
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/upload/non-existent.png");
        request.setContextPath("");

        ResponseEntity<InputStreamResource> response = controller.showFile(request);
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testShowFilePathTraversalBlocked() throws IOException {
        // 4. 路径遍历攻击返回 404
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/upload/../../etc/passwd");
        request.setContextPath("");

        ResponseEntity<InputStreamResource> response = controller.showFile(request);
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
