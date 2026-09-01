package com.fugary.simple.api.git;

import com.fugary.simple.api.imports.markdown.MarkdownDocImporterImpl;
import com.fugary.simple.api.service.apidoc.git.GitPlatformDocFetcher;
import com.fugary.simple.api.service.impl.apidoc.git.*;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GitDocContentProviderTest {

    static class TestableGitDocFetcher extends AbstractGitPlatformDocFetcher {
        @Override
        public boolean supports(GitRepoInfo.Platform platform) {
            return false;
        }

        @Override
        public SimpleResult<DocSourceData> fetchDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
            return SimpleResultUtils.createSimpleResult(DocSourceData.ofText("[]"));
        }

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
        TestableGitDocFetcher provider = new TestableGitDocFetcher();

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
        TestableGitDocFetcher provider = new TestableGitDocFetcher();

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
    public void testPlatformSupports() {
        GitLabDocFetcherImpl gitLabFetcher = new GitLabDocFetcherImpl();
        GitHubDocFetcherImpl gitHubFetcher = new GitHubDocFetcherImpl();
        GiteeDocFetcherImpl giteeFetcher = new GiteeDocFetcherImpl();

        Assertions.assertTrue(gitLabFetcher.supports(GitRepoInfo.Platform.GITLAB));
        Assertions.assertFalse(gitLabFetcher.supports(GitRepoInfo.Platform.GITHUB));
        Assertions.assertFalse(gitLabFetcher.supports(GitRepoInfo.Platform.GITEE));

        Assertions.assertTrue(gitHubFetcher.supports(GitRepoInfo.Platform.GITHUB));
        Assertions.assertFalse(gitHubFetcher.supports(GitRepoInfo.Platform.GITLAB));

        Assertions.assertTrue(giteeFetcher.supports(GitRepoInfo.Platform.GITEE));
        Assertions.assertFalse(giteeFetcher.supports(GitRepoInfo.Platform.GITHUB));
    }

    @Test
    public void testProviderRouting() {
        GitLabDocFetcherImpl gitLabFetcher = new GitLabDocFetcherImpl() {
            @Override
            public SimpleResult<DocSourceData> fetchDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
                return SimpleResultUtils.createSimpleResult(DocSourceData.ofText("[{\"path\":\"gitlab.md\",\"content\":\"gitlab\"}]"));
            }
        };
        GitHubDocFetcherImpl gitHubFetcher = new GitHubDocFetcherImpl() {
            @Override
            public SimpleResult<DocSourceData> fetchDocs(GitRepoInfo repoInfo, UrlWithAuthVo source) {
                return SimpleResultUtils.createSimpleResult(DocSourceData.ofText("[{\"path\":\"github.md\",\"content\":\"github\"}]"));
            }
        };

        GitDocContentProviderImpl provider = new GitDocContentProviderImpl(Arrays.asList(gitLabFetcher, gitHubFetcher));

        // 1. GitLab 路由
        GitRepoInfo gitLabRepo = GitRepoInfo.builder().platform(GitRepoInfo.Platform.GITLAB).build();
        SimpleResult<DocSourceData> gitLabRes = provider.getContent(gitLabRepo, new UrlWithAuthVo());
        Assertions.assertTrue(gitLabRes.isSuccess());
        Assertions.assertTrue(gitLabRes.getResultData().getTextContent().contains("gitlab.md"));

        // 2. GitHub 路由
        GitRepoInfo gitHubRepo = GitRepoInfo.builder().platform(GitRepoInfo.Platform.GITHUB).build();
        SimpleResult<DocSourceData> gitHubRes = provider.getContent(gitHubRepo, new UrlWithAuthVo());
        Assertions.assertTrue(gitHubRes.isSuccess());
        Assertions.assertTrue(gitHubRes.getResultData().getTextContent().contains("github.md"));

        // 3. 不支持的平台（Gitee 未注册）
        GitRepoInfo giteeRepo = GitRepoInfo.builder().platform(GitRepoInfo.Platform.GITEE).build();
        SimpleResult<DocSourceData> giteeRes = provider.getContent(giteeRepo, new UrlWithAuthVo());
        Assertions.assertFalse(giteeRes.isSuccess());
        Assertions.assertTrue(giteeRes.getMessage().contains("暂不支持的 Git 服务平台"));

        // 4. 空参数
        Assertions.assertFalse(provider.getContent(null, null).isSuccess());
        Assertions.assertFalse(provider.getContent(new GitRepoInfo(), null).isSuccess());
    }

    @Test
    public void testGitLabPagination() {
        // 模拟 GitLab 超过 100 个条目，需要分页 2 次拉取
        List<String> requestedUrls = new ArrayList<>();
        GitLabDocFetcherImpl paginatedGitLabFetcher = new GitLabDocFetcherImpl() {
            @Override
            protected SimpleResult<String> sendGetRequest(String url, UrlWithAuthVo source, GitRepoInfo repoInfo) {
                requestedUrls.add(url);
                if (url.contains("/repository/tree")) {
                    if (url.endsWith("&page=1")) {
                        // 返回 100 个 blob 模拟第一页满页
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 1; i <= 100; i++) {
                            if (i > 1) sb.append(",");
                            sb.append("{\"type\":\"blob\",\"path\":\"docs/doc_").append(i).append(".md\"}");
                        }
                        sb.append("]");
                        return SimpleResultUtils.createSimpleResult(sb.toString());
                    } else if (url.endsWith("&page=2")) {
                        // 第二页 5 个 blob（< 100，触发终止）
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 101; i <= 105; i++) {
                            if (i > 101) sb.append(",");
                            sb.append("{\"type\":\"blob\",\"path\":\"docs/doc_").append(i).append(".md\"}");
                        }
                        sb.append("]");
                        return SimpleResultUtils.createSimpleResult(sb.toString());
                    }
                }
                if (url.contains("/repository/files/")) {
                    return SimpleResultUtils.createSimpleResult("# Doc Content");
                }
                return SimpleResultUtils.createError(404, "Not Found");
            }
        };

        GitRepoInfo repoInfo = GitRepoInfo.builder()
                .platform(GitRepoInfo.Platform.GITLAB)
                .serverUrl("https://gitlab.example.com")
                .projectPath("group/my-project")
                .branch("main")
                .subPath("docs")
                .repo("my-project")
                .build();

        SimpleResult<DocSourceData> result = paginatedGitLabFetcher.fetchDocs(repoInfo, new UrlWithAuthVo());
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertNotNull(result.getResultData());
        // 验证请求了 page=1 和 page=2
        Assertions.assertTrue(requestedUrls.stream().anyMatch(u -> u.contains("page=1")));
        Assertions.assertTrue(requestedUrls.stream().anyMatch(u -> u.contains("page=2")));
        // 验证 105 个 md 文件都被成功拉取
        String json = result.getResultData().getTextContent();
        Assertions.assertTrue(json.contains("doc_1.md"));
        Assertions.assertTrue(json.contains("doc_100.md"));
        Assertions.assertTrue(json.contains("doc_105.md"));
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

    @Test
    public void testIsNonRetryableError() {
        // 401/403/404 等不可恢复错误
        Assertions.assertTrue(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "访问 Git 仓库失败(HTTP/1.1 401 Unauthorized)")));
        Assertions.assertTrue(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "访问 Git 仓库失败(HTTP/1.1 403 Forbidden)")));
        Assertions.assertTrue(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "访问 Git 仓库失败(HTTP/1.1 404 Not Found)")));
        Assertions.assertTrue(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "未找到文件")));
        Assertions.assertTrue(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "请填入 Token")));

        // 网络波动、连接重置、超时等可重试错误
        Assertions.assertFalse(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "Git HTTP 请求异常: Connection reset")));
        Assertions.assertFalse(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "Git HTTP 请求异常: Connect timed out")));
        Assertions.assertFalse(SimpleHttpClientUtils.isNonRetryableError(com.fugary.simple.api.utils.SimpleResultUtils.createError(2009, "Git API 请求失败: HTTP/1.1 502 Bad Gateway")));
        Assertions.assertFalse(SimpleHttpClientUtils.isNonRetryableError(null));
    }
}
