package com.fugary.simple.api.service.impl.apidoc.asset;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class DocAssetStorageServiceImplTest {

    private DocAssetStorageServiceImpl service;
    private File uploadDirectory;

    @BeforeEach
    void setUp() throws IOException {
        service = new DocAssetStorageServiceImpl();
        uploadDirectory = Files.createTempDirectory("doc-assets-").toFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(uploadDirectory);
    }

    @Test
    void resolvesImageOnlyFromCurrentProjectDirectory() throws IOException {
        File projectDirectory = new File(uploadDirectory, "docs/current-project");
        FileUtils.forceMkdir(projectDirectory);
        File imageFile = new File(projectDirectory, "architecture.png");
        FileUtils.writeStringToFile(imageFile, "CURRENT_PROJECT", StandardCharsets.UTF_8);

        File resolved = service.resolveImageFile(uploadDirectory.getAbsolutePath(),
                "docs/current-project/architecture.png", "architecture.png", "current-project");

        Assertions.assertNotNull(resolved);
        Assertions.assertEquals(imageFile.getCanonicalFile(), resolved.getCanonicalFile());
    }

    @Test
    void resolvesImageFromAnotherProjectDirectory() throws IOException {
        File otherProjectDirectory = new File(uploadDirectory, "docs/other-project");
        FileUtils.forceMkdir(otherProjectDirectory);
        File imageFile = new File(otherProjectDirectory, "architecture.png");
        FileUtils.writeStringToFile(imageFile, "OTHER_PROJECT", StandardCharsets.UTF_8);

        File resolved = service.resolveImageFile(uploadDirectory.getAbsolutePath(),
                "docs/other-project/architecture.png", "architecture.png", "current-project");

        Assertions.assertNotNull(resolved);
        Assertions.assertEquals(imageFile.getCanonicalFile(), resolved.getCanonicalFile());
    }

    @Test
    void rejectsPathTraversalOutsideUploadDirectory() throws IOException {
        File outsideFile = new File(uploadDirectory.getParentFile(), "secret.png");
        FileUtils.writeStringToFile(outsideFile, "SECRET", StandardCharsets.UTF_8);

        try {
            File resolved = service.resolveImageFile(uploadDirectory.getAbsolutePath(),
                    "../secret.png", "secret.png", "current-project");

            Assertions.assertNull(resolved);
        } finally {
            FileUtils.deleteQuietly(outsideFile);
        }
    }

    @Test
    void rejectsImageWhenProjectCodeIsMissing() throws IOException {
        File rootImage = new File(uploadDirectory, "legacy.png");
        FileUtils.writeStringToFile(rootImage, "LEGACY", StandardCharsets.UTF_8);

        File resolved = service.resolveImageFile(uploadDirectory.getAbsolutePath(),
                "legacy.png", "legacy.png", null);

        Assertions.assertNull(resolved);
    }

    @Test
    void rejectsSymbolicLinkEscapingCurrentProjectDirectory() throws IOException {
        Path projectDirectory = new File(uploadDirectory, "docs/current-project").toPath();
        Files.createDirectories(projectDirectory);
        Path outsideImage = new File(uploadDirectory, "outside.png").toPath();
        Files.write(outsideImage, "OUTSIDE".getBytes(StandardCharsets.UTF_8));
        Path symbolicLink = projectDirectory.resolve("linked.png");

        try {
            Files.createSymbolicLink(symbolicLink, outsideImage);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return;
        }

        File resolved = service.resolveImageFile(uploadDirectory.getAbsolutePath(),
                "docs/current-project/linked.png", "linked.png", "current-project");

        Assertions.assertNull(resolved);
    }

    @Test
    void inlineAndExtractBase64ImagesRoundTrip() throws Exception {
        java.lang.reflect.Field field = DocAssetStorageServiceImpl.class.getDeclaredField("baseDataDir");
        field.setAccessible(true);
        field.set(service, uploadDirectory.getAbsolutePath());

        byte[] originalBytes = new byte[]{10, 20, 30, 40, 50};
        String base64 = java.util.Base64.getEncoder().encodeToString(originalBytes);
        String markdownWithBase64 = "这是一个测试图片: ![test](data:image/png;base64," + base64 + ") 结束";

        String extracted = service.extractAndSaveBase64Images(markdownWithBase64, "test-proj");
        Assertions.assertTrue(extracted.contains("/upload/docs/test-proj/"));
        Assertions.assertFalse(extracted.contains("data:image/png;base64"));

        String localUrl = extracted.substring(extracted.indexOf("/upload/docs/test-proj/"), extracted.indexOf(")"));
        String fileName = localUrl.substring(localUrl.lastIndexOf('/') + 1);
        File savedFile = new File(uploadDirectory, "upload/docs/test-proj/" + fileName);
        Assertions.assertTrue(savedFile.exists());
        Assertions.assertArrayEquals(originalBytes, FileUtils.readFileToByteArray(savedFile));

        String markdownWithLocalUrl = "这是一张本地图片: ![](" + localUrl + ")";
        String inlined = service.inlineImagesAsBase64(markdownWithLocalUrl, "test-proj", new java.util.HashMap<>());
        Assertions.assertTrue(inlined.contains("data:image/png;base64," + base64));
    }
}