package com.fugary.simple.api.content;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.service.apidoc.content.UrlDocContentProviderImpl;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class UrlDocContentProviderTest {

    static class TestableUrlDocContentProvider extends UrlDocContentProviderImpl {
        @Override
        public boolean isZipContent(byte[] bytes, HttpResponse response, String url) {
            return super.isZipContent(bytes, response, url);
        }

        @Override
        public void processAuth(HttpRequest request, UrlWithAuthVo source) {
            super.processAuth(request, source);
        }
    }

    @Test
    public void testIsZipContent() {
        TestableUrlDocContentProvider provider = new TestableUrlDocContentProvider();

        // 1. ZIP magic bytes (PK..)
        byte[] zipBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
        Assertions.assertTrue(provider.isZipContent(zipBytes, null, "http://example.com/download"));

        // 2. Normal text bytes
        byte[] textBytes = "# Hello World".getBytes(StandardCharsets.UTF_8);
        Assertions.assertFalse(provider.isZipContent(textBytes, null, "http://example.com/README.md"));

        // 3. Short / Null bytes
        Assertions.assertFalse(provider.isZipContent(new byte[]{0x50, 0x4B}, null, "http://example.com/test"));
        Assertions.assertFalse(provider.isZipContent(null, null, "http://example.com/test"));

        // 4. Content-Type header with zip
        HttpResponse response = Mockito.mock(HttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.when(response.getEntity()).thenReturn(entity);
        Mockito.when(entity.getContentType()).thenReturn(new BasicHeader("Content-Type", "application/zip"));
        Assertions.assertTrue(provider.isZipContent(zipBytes, response, "http://example.com/archive"));

        // 5. URL ending in .zip
        Assertions.assertTrue(provider.isZipContent(zipBytes, null, "http://example.com/archive.zip?token=123"));
    }

    @Test
    public void testProcessAuth() {
        TestableUrlDocContentProvider provider = new TestableUrlDocContentProvider();

        // 1. Basic Auth
        HttpRequest basicReq = new BasicHttpRequest("GET", "/api/docs");
        UrlWithAuthVo basicVo = new UrlWithAuthVo();
        basicVo.setAuthType(ApiDocConstants.AUTH_TYPE_BASIC);
        basicVo.setAuthContent("{\"userName\":\"admin\",\"userPassword\":\"123456\"}");
        provider.processAuth(basicReq, basicVo);
        Header basicAuthHeader = basicReq.getFirstHeader("Authorization");
        Assertions.assertNotNull(basicAuthHeader);
        Assertions.assertTrue(basicAuthHeader.getValue().startsWith("Basic "));

        // 2. Token Auth (JSON)
        HttpRequest tokenReq = new BasicHttpRequest("GET", "/api/docs");
        UrlWithAuthVo tokenVo = new UrlWithAuthVo();
        tokenVo.setAuthType("token");
        tokenVo.setAuthContent("{\"token\":\"my-secret-token\",\"headerName\":\"Authorization\",\"tokenPrefix\":\"Bearer\"}");
        provider.processAuth(tokenReq, tokenVo);
        Header tokenAuthHeader = tokenReq.getFirstHeader("Authorization");
        Assertions.assertNotNull(tokenAuthHeader);
        Assertions.assertEquals("Bearer my-secret-token", tokenAuthHeader.getValue());

        // 3. Token Auth (GitLab PRIVATE-TOKEN)
        HttpRequest gitlabReq = new BasicHttpRequest("GET", "/api/docs");
        UrlWithAuthVo gitlabVo = new UrlWithAuthVo();
        gitlabVo.setAuthType("token");
        gitlabVo.setAuthContent("{\"token\":\"glpat-123456\",\"headerName\":\"PRIVATE-TOKEN\",\"tokenPrefix\":\"\"}");
        provider.processAuth(gitlabReq, gitlabVo);
        Header gitlabHeader = gitlabReq.getFirstHeader("PRIVATE-TOKEN");
        Assertions.assertNotNull(gitlabHeader);
        Assertions.assertEquals("glpat-123456", gitlabHeader.getValue());

        // 4. Raw Token Auth (plain string)
        HttpRequest rawReq = new BasicHttpRequest("GET", "/api/docs");
        UrlWithAuthVo rawVo = new UrlWithAuthVo();
        rawVo.setAuthType("token");
        rawVo.setAuthContent("simple-token-123");
        provider.processAuth(rawReq, rawVo);
        Header rawHeader = rawReq.getFirstHeader("Authorization");
        Assertions.assertNotNull(rawHeader);
        Assertions.assertEquals("Bearer simple-token-123", rawHeader.getValue());
    }

    @Test
    public void testDownloadedZipImportFlow() throws IOException {
        // 模拟从 URL 下载到 ZIP 二进制流后转换为 Base64，并成功被 MarkdownDocImporterImpl 解析
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("01-start/README.md"));
            zos.write("# 开始使用\n\n欢迎使用系统。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("02-api/guide.md"));
            zos.write("# 接口指南\n\nAPI 文档说明。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        byte[] zipBytes = baos.toByteArray();
        String base64Content = Base64.getEncoder().encodeToString(zipBytes);

        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        Assertions.assertTrue(importer.match(base64Content));

        ExportApiProjectVo projectVo = importer.doImport(base64Content);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(2, projectVo.getFolders().size());
        Assertions.assertEquals("开始使用", projectVo.getFolders().get(0).getDocs().get(0).getDocName());
    }
}
