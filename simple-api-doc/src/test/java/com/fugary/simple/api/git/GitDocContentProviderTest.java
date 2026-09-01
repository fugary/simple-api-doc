package com.fugary.simple.api.git;

import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.service.impl.apidoc.git.GitDocContentProviderImpl;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
    public void testJGitLocalRepoDeepSubfolderPull() throws Exception {
        // 1. 创建本地临时 Git 仓库，模拟多级深层子目录结构
        File localRepoDir = Files.createTempDirectory("test_jgit_local_repo_").toFile();
        try {
            try (Git git = Git.init().setDirectory(localRepoDir).call()) {
                // 仓库根目录下的无关文件
                File rootPom = new File(localRepoDir, "pom.xml");
                Files.writeString(rootPom.toPath(), "<project></project>", StandardCharsets.UTF_8);

                // 深层子目录: NewHRBox/new-hrbox-parent/docs/01-guide/install.md
                File docFolder = new File(localRepoDir, "NewHRBox/new-hrbox-parent/docs/01-guide");
                docFolder.mkdirs();
                File installMd = new File(docFolder, "install.md");
                Files.writeString(installMd.toPath(), "# 安装指南\n\nJDK 11 安装步骤。", StandardCharsets.UTF_8);

                // 深层子目录下的 README.md
                File readmeMd = new File(localRepoDir, "NewHRBox/new-hrbox-parent/docs/README.md");
                Files.writeString(readmeMd.toPath(), "# 首页文档\n\n欢迎使用。", StandardCharsets.UTF_8);

                // 提交到 git
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Initial commit").call();
            }

            // 2. 调用 GitDocContentProviderImpl 执行拉取
            GitDocContentProviderImpl provider = new GitDocContentProviderImpl();
            GitRepoInfo repoInfo = GitRepoInfo.builder()
                    .cloneUrl(localRepoDir.toURI().toString())
                    .branch("master")
                    .subPath("NewHRBox/new-hrbox-parent/docs")
                    .repo("new-hrbox-parent")
                    .build();

            SimpleResult<DocSourceData> result = provider.getContent(repoInfo, new UrlWithAuthVo());
            Assertions.assertTrue(result.isSuccess());
            Assertions.assertNotNull(result.getResultData());

            String json = result.getResultData().getTextContent();
            // 验证深层前缀被剥离，保留相对路径
            Assertions.assertTrue(json.contains("01-guide/install.md"));
            Assertions.assertTrue(json.contains("README.md"));
            // 验证根目录无关文件被排除
            Assertions.assertFalse(json.contains("pom.xml"));

            // 3. 验证与 Markdown 导入器完整集成
            MarkdownDocImporterImpl importer = new MarkdownDocImporterImpl();
            ExportApiProjectVo projectVo = importer.doImport(result.getResultData());
            Assertions.assertNotNull(projectVo);
            Assertions.assertEquals("首页文档", projectVo.getDocs().get(0).getDocName());
            Assertions.assertEquals("安装指南", projectVo.getFolders().get(0).getDocs().get(0).getDocName());
        } finally {
            FileUtils.deleteDirectory(localRepoDir);
        }
    }

    @Test
    public void testVirtualJsonIntegrationWithImporter() {
        // 模拟组装的 Virtual JSON
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

    @Test
    public void testIsNonRetryableError() {
        // 401/403/404 等不可恢复错误
        Assertions.assertTrue(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "访问 Git 仓库失败(HTTP/1.1 401 Unauthorized)")));
        Assertions.assertTrue(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "访问 Git 仓库失败(HTTP/1.1 403 Forbidden)")));
        Assertions.assertTrue(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "访问 Git 仓库失败(HTTP/1.1 404 Not Found)")));
        Assertions.assertTrue(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "未找到文件")));
        Assertions.assertTrue(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "请填入 Token")));

        // 网络波动、连接重置、超时等可重试错误
        Assertions.assertFalse(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "Git HTTP 请求异常: Connection reset")));
        Assertions.assertFalse(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "Git HTTP 请求异常: Connect timed out")));
        Assertions.assertFalse(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "Git API 请求失败: HTTP/1.1 502 Bad Gateway")));
        Assertions.assertFalse(com.fugary.simple.api.utils.http.SimpleHttpClientUtils.isNonRetryableError(null));
    }
}
