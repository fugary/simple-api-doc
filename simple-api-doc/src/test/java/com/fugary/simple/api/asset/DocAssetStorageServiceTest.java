package com.fugary.simple.api.asset;

import com.fugary.simple.api.service.impl.apidoc.asset.DocAssetStorageServiceImpl;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DocAssetStorageServiceTest {

    private DocAssetStorageServiceImpl storageService;
    private Path tempDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("simple_api_doc_test_upload");
        storageService = new DocAssetStorageServiceImpl();
        ReflectionTestUtils.setField(storageService, "baseDataDir", tempDir.toString());
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    public void testSaveImageAndDeduplication() {
        byte[] fakeImageBytes = "FAKE_PNG_BINARY_CONTENT_12345".getBytes(StandardCharsets.UTF_8);
        String expectedMd5 = DigestUtils.md5Hex(fakeImageBytes);

        String url1 = storageService.saveImage(fakeImageBytes, "logo.png", "my-test");
        Assertions.assertEquals("/upload/docs/my-test/" + expectedMd5 + ".png", url1);

        // 验证文件是否真实保存到了指定目录
        File savedFile = new File(tempDir.toFile(), "upload/docs/my-test/" + expectedMd5 + ".png");
        Assertions.assertTrue(savedFile.exists());
        Assertions.assertEquals(fakeImageBytes.length, savedFile.length());

        // 第二次保存完全相同的内容，测试幂等性
        String url2 = storageService.saveImage(fakeImageBytes, "logo.png", "my-test");
        Assertions.assertEquals(url1, url2);
    }

    @Test
    public void testReplaceRelativeImages() {
        String markdown = "# 测试页面\n\n" +
                "这是一个测试页面\n\n" +
                "![图片](./logo.png)\n" +
                "![架构图](images/arch.png)\n" +
                "![公共Logo](../assets/logo.png)\n" +
                "<img src=\"./logo.png\" width=\"300\" />\n" +
                "![外部链接](https://example.com/demo.png)\n";

        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("docs/logo.png", "/upload/docs/my-test/111111.png");
        imageMap.put("docs/images/arch.png", "/upload/docs/my-test/222222.png");
        imageMap.put("assets/logo.png", "/upload/docs/my-test/333333.png");

        String docPath = "docs/test.md";
        String replaced = storageService.replaceRelativeImages(markdown, docPath, imageMap);

        Assertions.assertTrue(replaced.contains("![图片](/upload/docs/my-test/111111.png)"));
        Assertions.assertTrue(replaced.contains("![架构图](/upload/docs/my-test/222222.png)"));
        Assertions.assertTrue(replaced.contains("![公共Logo](/upload/docs/my-test/333333.png)"));
        Assertions.assertTrue(replaced.contains("<img src=\"/upload/docs/my-test/111111.png\" width=\"300\" />"));
        Assertions.assertTrue(replaced.contains("![外部链接](https://example.com/demo.png)"));
    }

    @Test
    public void testIsImageFile() {
        Assertions.assertTrue(storageService.isImageFile("logo.png"));
        Assertions.assertTrue(storageService.isImageFile("images/arch.PNG"));
        Assertions.assertTrue(storageService.isImageFile("photo.jpg"));
        Assertions.assertTrue(storageService.isImageFile("icon.svg"));
        Assertions.assertTrue(storageService.isImageFile("banner.webp"));
        Assertions.assertTrue(storageService.isImageFile("anim.gif"));

        Assertions.assertFalse(storageService.isImageFile("test.md"));
        Assertions.assertFalse(storageService.isImageFile("Main.java"));
        Assertions.assertFalse(storageService.isImageFile("pom.xml"));
        Assertions.assertFalse(storageService.isImageFile(null));
        Assertions.assertFalse(storageService.isImageFile(""));
    }
}
