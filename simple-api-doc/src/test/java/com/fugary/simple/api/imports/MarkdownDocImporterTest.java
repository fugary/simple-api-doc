package com.fugary.simple.api.imports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MarkdownDocImporterTest {

    @Test
    public void testMatch() {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();

        Assertions.assertTrue(importer.match("# Quick Start\nSome markdown text"));
        Assertions.assertTrue(importer.match("---\ntitle: Guide\n---\n# Content"));
        Assertions.assertTrue(importer.match("```json\n{\"test\": 1}\n```"));
        Assertions.assertTrue(importer.match("[{\"path\": \"doc.md\", \"content\": \"# Test\"}]"));
        Assertions.assertTrue(importer.match("UEsDBBQAAAAIA..."));
        Assertions.assertTrue(importer.match("test122\n\n### testabc\n\ntest222"));
        Assertions.assertTrue(importer.match("Just plain text notes."));

        // OpenAPI JSON / Swagger YAML / invalid JSON should not match Markdown
        Assertions.assertFalse(importer.match("{\n  \"openapi\": \"3.0.1\"\n}"));
        Assertions.assertFalse(importer.match("{\n  \"key\": \"value\"\n}"));
        Assertions.assertFalse(importer.match("openapi: 3.0.0\ninfo:\n  title: Sample"));
        Assertions.assertFalse(importer.match("swagger: '2.0'\ninfo:\n  title: Sample"));

        Assertions.assertFalse(importer.match(""));
        Assertions.assertFalse(importer.match((String) null));
        Assertions.assertFalse(importer.match((com.fugary.simple.api.web.vo.imports.DocSourceData) null));
    }

    @Test
    public void testSingleMarkdownImport() {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        String md = "---\ntitle: 快速上手\norder: 10\ndescription: 这是一个入门指南\n---\n# 快速上手指南\n这是正文内容。";

        ExportApiProjectVo projectVo = importer.doImport(md);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals("快速上手", projectVo.getProjectName());
        Assertions.assertEquals(1, projectVo.getDocs().size());

        ExportApiDocVo doc = projectVo.getDocs().get(0);
        Assertions.assertEquals("快速上手", doc.getDocName());
        Assertions.assertEquals(10, doc.getSortId());
        Assertions.assertEquals("这是一个入门指南", doc.getDescription());
        Assertions.assertEquals(ApiDocConstants.DOC_TYPE_MD, doc.getDocType());
        Assertions.assertTrue(doc.getDocContent().contains("这是正文内容。"));
        Assertions.assertFalse(doc.getDocContent().contains("---"));
    }

    @Test
    public void testH1TitleAndNumericSortFallback() {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        String md = "# 核心架构设计\n\n系统由前端和后端构成。";

        ExportApiProjectVo projectVo = importer.doImport(md);
        Assertions.assertNotNull(projectVo);
        ExportApiDocVo doc = projectVo.getDocs().get(0);
        Assertions.assertEquals("核心架构设计", doc.getDocName());
        Assertions.assertEquals("核心架构设计", projectVo.getProjectName());
        Assertions.assertEquals(1, doc.getSortId()); // README/index default
    }

    @Test
    public void testMarkdownWithH3Heading() {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        String md = "test122\n\n### testabc\n\ntest222";

        ExportApiProjectVo projectVo = importer.doImport(md);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(1, projectVo.getDocs().size());
        ExportApiDocVo doc = projectVo.getDocs().get(0);
        Assertions.assertEquals("testabc", doc.getDocName());
        Assertions.assertEquals("testabc", projectVo.getProjectName());
    }

    @Test
    public void testMarkdownWithPlainTextOnly() {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        String md = "Just some notes without any title or heading.";

        ExportApiProjectVo projectVo = importer.doImport(md);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(1, projectVo.getDocs().size());
        ExportApiDocVo doc = projectVo.getDocs().get(0);
        Assertions.assertEquals("README", doc.getDocName());
        Assertions.assertEquals("README", projectVo.getProjectName());
        Assertions.assertEquals(md, doc.getDocContent());
    }

    @Test
    public void testMarkdownWithCustomFileName() {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        String md = "Just some notes without any title or heading.";
        ApiProjectImportVo importVo = new ApiProjectImportVo();
        importVo.setFileName("install-guide.md");

        ExportApiProjectVo projectVo = importer.doImport(md, importVo);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(1, projectVo.getDocs().size());
        ExportApiDocVo doc = projectVo.getDocs().get(0);
        Assertions.assertEquals("install-guide.md", doc.getDocKey());
        Assertions.assertEquals("install-guide", doc.getDocName());
    }

    @Test
    public void testZipArchiveMultiLevelImport() throws IOException {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();

        // 创建多级虚拟 ZIP 文件
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            // 根目录文档
            zos.putNextEntry(new ZipEntry("README.md"));
            zos.write("# 我的技术文档项目\n\n欢迎阅读。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 01-guide/01-installation.md
            zos.putNextEntry(new ZipEntry("01-guide/01-installation.md"));
            zos.write("# 安装说明\n\n请先安装 JDK 11。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 01-guide/02-configuration.md 带 Frontmatter
            zos.putNextEntry(new ZipEntry("01-guide/02-configuration.md"));
            zos.write("---\ntitle: 配置指南\norder: 250\nlocked: true\n---\n# 配置步骤\n修改 application.yml。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 01-guide/02-advanced/01-auth.md (二级子目录)
            zos.putNextEntry(new ZipEntry("01-guide/02-advanced/01-auth.md"));
            zos.write("# 认证机制\n\nJWT Token 鉴权。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 02-faq/README.md
            zos.putNextEntry(new ZipEntry("02-faq/README.md"));
            zos.write("# 常见问题解答\n\nFAQ 列表。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // macOS 垃圾目录（应被过滤）
            zos.putNextEntry(new ZipEntry("__MACOSX/._README.md"));
            zos.write(new byte[]{0, 1, 2});
            zos.closeEntry();
        }

        String base64Zip = Base64.getEncoder().encodeToString(baos.toByteArray());
        ExportApiProjectVo projectVo = importer.doImport(base64Zip);

        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals("我的技术文档项目", projectVo.getProjectName());

        // 根目录文档检查 (README.md)
        Assertions.assertEquals(1, projectVo.getDocs().size());
        ExportApiDocVo rootDoc = projectVo.getDocs().get(0);
        Assertions.assertEquals("我的技术文档项目", rootDoc.getDocName());
        Assertions.assertEquals(1, rootDoc.getSortId());

        // 顶级文件夹检查 (guide, faq)
        List<ExportApiFolderVo> topFolders = projectVo.getFolders();
        Assertions.assertEquals(2, topFolders.size());

        ExportApiFolderVo guideFolder = topFolders.stream().filter(f -> "guide".equals(f.getFolderName())).findFirst().orElse(null);
        ExportApiFolderVo faqFolder = topFolders.stream().filter(f -> "faq".equals(f.getFolderName())).findFirst().orElse(null);

        Assertions.assertNotNull(guideFolder);
        Assertions.assertEquals("01-guide", guideFolder.getFolderCode());
        Assertions.assertEquals("guide", guideFolder.getFolderName());
        Assertions.assertEquals(100, guideFolder.getSortId());
        Assertions.assertEquals(2, guideFolder.getDocs().size()); // 01-installation, 02-configuration
        Assertions.assertEquals(1, guideFolder.getFolders().size()); // 02-advanced

        // 检查 guide 文件夹下的文档
        ExportApiDocVo installDoc = guideFolder.getDocs().stream().filter(d -> "安装说明".equals(d.getDocName())).findFirst().orElse(null);
        ExportApiDocVo configDoc = guideFolder.getDocs().stream().filter(d -> "配置指南".equals(d.getDocName())).findFirst().orElse(null);
        Assertions.assertNotNull(installDoc);
        Assertions.assertEquals("安装说明", installDoc.getSummary());
        Assertions.assertEquals(100, installDoc.getSortId());

        Assertions.assertNotNull(configDoc);
        Assertions.assertEquals("配置指南", configDoc.getSummary());
        Assertions.assertEquals(250, configDoc.getSortId());
        Assertions.assertTrue(Boolean.TRUE.equals(configDoc.getLocked()));

        // 检查二级子文件夹 advanced
        ExportApiFolderVo advancedFolder = guideFolder.getFolders().get(0);
        Assertions.assertEquals("02-advanced", advancedFolder.getFolderCode());
        Assertions.assertEquals("advanced", advancedFolder.getFolderName());
        Assertions.assertEquals(200, advancedFolder.getSortId());
        Assertions.assertEquals(1, advancedFolder.getDocs().size());
        Assertions.assertEquals("认证机制", advancedFolder.getDocs().get(0).getDocName());

        // 检查 faq 文件夹
        Assertions.assertNotNull(faqFolder);
        Assertions.assertEquals("02-faq", faqFolder.getFolderCode());
        Assertions.assertEquals("faq", faqFolder.getFolderName());
        Assertions.assertEquals(200, faqFolder.getSortId());
        Assertions.assertEquals(1, faqFolder.getDocs().size());
        Assertions.assertEquals("常见问题解答", faqFolder.getDocs().get(0).getDocName());
    }

    @Test
    public void testJsonVirtualFilesImport() {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        String json = "[\n" +
                "  {\"path\": \"01-start/intro.md\", \"content\": \"# 开始\\n介绍内容\"},\n" +
                "  {\"path\": \"02-api/guide.md\", \"content\": \"---\\ntitle: API指南\\norder: 50\\n---\\n# 接口说明\"}\n" +
                "]";

        ExportApiProjectVo projectVo = importer.doImport(json);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(2, projectVo.getFolders().size());

        ExportApiFolderVo startFolder = projectVo.getFolders().stream().filter(f -> "start".equals(f.getFolderName())).findFirst().orElse(null);
        Assertions.assertNotNull(startFolder);
        Assertions.assertEquals("01-start", startFolder.getFolderCode());
        Assertions.assertEquals("start", startFolder.getFolderName());
        Assertions.assertEquals(1, startFolder.getDocs().size());
        Assertions.assertEquals("开始", startFolder.getDocs().get(0).getDocName());

        ExportApiFolderVo apiFolder = projectVo.getFolders().stream().filter(f -> "api".equals(f.getFolderName())).findFirst().orElse(null);
        Assertions.assertNotNull(apiFolder);
        Assertions.assertEquals("02-api", apiFolder.getFolderCode());
        Assertions.assertEquals("api", apiFolder.getFolderName());
        Assertions.assertEquals(1, apiFolder.getDocs().size());
        Assertions.assertEquals("API指南", apiFolder.getDocs().get(0).getDocName());
        Assertions.assertEquals(50, apiFolder.getDocs().get(0).getSortId());
    }

    @Test
    public void testZipArchiveWithGbkCharset() throws IOException {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();

        // 创建使用 GBK 编码文件名和目录的 ZIP 压缩包（模拟 Windows 下打包）
        Charset gbk = Charset.forName("GBK");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, gbk)) {
            zos.putNextEntry(new ZipEntry("01-开发指南/01-快速上手.md"));
            zos.write("# 快速上手\n\n欢迎使用本系统。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("02-常见问题/FAQ说明.md"));
            zos.write("# 常见问题\n\n问题排查指南。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        String base64Zip = Base64.getEncoder().encodeToString(baos.toByteArray());
        ExportApiProjectVo projectVo = importer.doImport(base64Zip);

        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(2, projectVo.getFolders().size());
        ExportApiFolderVo guideFolder = projectVo.getFolders().stream()
                .filter(f -> "开发指南".equals(f.getFolderName())).findFirst().orElse(null);
        Assertions.assertNotNull(guideFolder);
        Assertions.assertEquals("01-开发指南", guideFolder.getFolderCode());
        Assertions.assertEquals(1, guideFolder.getDocs().size());
        Assertions.assertEquals("快速上手", guideFolder.getDocs().get(0).getDocName());
    }

    @Test
    public void testDocSourceDataDirectBinaryImport() throws IOException {
        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("01-guide/intro.md"));
            zos.write("# 系统简介\n\n这是系统说明。".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        byte[] zipBytes = baos.toByteArray();
        com.fugary.simple.api.web.vo.imports.DocSourceData sourceData = com.fugary.simple.api.web.vo.imports.DocSourceData.ofBinary(zipBytes, "docs.zip");

        Assertions.assertTrue(importer.match(sourceData));
        ExportApiProjectVo projectVo = importer.doImport(sourceData, null);

        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(1, projectVo.getFolders().size());
        Assertions.assertEquals("guide", projectVo.getFolders().get(0).getFolderName());
        Assertions.assertEquals("系统简介", projectVo.getFolders().get(0).getDocs().get(0).getDocName());
    }
}
