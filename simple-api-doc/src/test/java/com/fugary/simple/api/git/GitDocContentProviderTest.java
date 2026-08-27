package com.fugary.simple.api.git;

import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.service.impl.apidoc.git.GitDocContentProviderImpl;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GitDocContentProviderTest {

    static class TestableGitDocContentProvider extends GitDocContentProviderImpl {
        @Override
        public String stripSubPathPrefix(String path, String subPath) {
            return super.stripSubPathPrefix(path, subPath);
        }

        @Override
        public boolean isMarkdownFile(String path) {
            return super.isMarkdownFile(path);
        }
    }

    @Test
    public void testStripSubPathPrefix() {
        TestableGitDocContentProvider provider = new TestableGitDocContentProvider();

        // 1. Single level subPath
        Assertions.assertEquals("01-guide/install.md",
                provider.stripSubPathPrefix("docs/01-guide/install.md", "docs"));
        Assertions.assertEquals("README.md",
                provider.stripSubPathPrefix("docs/README.md", "docs"));

        // 2. Multi-level subPath
        Assertions.assertEquals("01-guide/install.md",
                provider.stripSubPathPrefix("NewHRBox/new-hrbox-parent/docs/01-guide/install.md", "NewHRBox/new-hrbox-parent/docs"));

        // 3. Leading / trailing slashes in subPath
        Assertions.assertEquals("01-guide/install.md",
                provider.stripSubPathPrefix("docs/01-guide/install.md", "/docs/"));

        // 4. Exact path match
        Assertions.assertEquals("install.md",
                provider.stripSubPathPrefix("docs/install.md", "docs/install.md"));

        // 5. Blank subPath
        Assertions.assertEquals("docs/01-guide/install.md",
                provider.stripSubPathPrefix("docs/01-guide/install.md", ""));
    }

    @Test
    public void testIsMarkdownFile() {
        TestableGitDocContentProvider provider = new TestableGitDocContentProvider();

        Assertions.assertTrue(provider.isMarkdownFile("README.md"));
        Assertions.assertTrue(provider.isMarkdownFile("docs/guide.markdown"));
        Assertions.assertTrue(provider.isMarkdownFile("docs/GUIDE.MD"));

        Assertions.assertFalse(provider.isMarkdownFile("src/Main.java"));
        Assertions.assertFalse(provider.isMarkdownFile("pom.xml"));
        Assertions.assertFalse(provider.isMarkdownFile("package.json"));
        Assertions.assertFalse(provider.isMarkdownFile(""));
        Assertions.assertFalse(provider.isMarkdownFile(null));
    }

    @Test
    public void testVirtualJsonIntegrationWithImporter() {
        // 模拟 GitDocContentProvider 抓取后组装的 Virtual JSON
        String virtualFilesJson = "[\n" +
                "  {\"path\": \"01-快速上手/01-安装指南.md\", \"content\": \"# 安装指南\\n\\nJDK 11 安装步骤。\"},\n" +
                "  {\"path\": \"02-接口说明/01-用户接口.md\", \"content\": \"---\\ntitle: 用户接口文档\\norder: 120\\n---\\n# 用户接口\\n\\nAPI 列表。\"}\n" +
                "]";

        MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
        Assertions.assertTrue(importer.match(virtualFilesJson));

        ExportApiProjectVo projectVo = importer.doImport(virtualFilesJson);
        Assertions.assertNotNull(projectVo);
        Assertions.assertEquals(2, projectVo.getFolders().size());

        Assertions.assertEquals("快速上手", projectVo.getFolders().get(0).getFolderName());
        Assertions.assertEquals("安装指南", projectVo.getFolders().get(0).getDocs().get(0).getDocName());

        Assertions.assertEquals("接口说明", projectVo.getFolders().get(1).getFolderName());
        Assertions.assertEquals("用户接口文档", projectVo.getFolders().get(1).getDocs().get(0).getDocName());
        Assertions.assertEquals(120, projectVo.getFolders().get(1).getDocs().get(0).getSortId());
    }
}
