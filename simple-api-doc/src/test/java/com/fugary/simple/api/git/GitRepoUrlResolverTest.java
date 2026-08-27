package com.fugary.simple.api.git;

import com.fugary.simple.api.utils.git.GitRepoUrlResolver;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GitRepoUrlResolverTest {

    @Test
    public void testGitLabCustomDomainAndPort() {
        String url = "https://git.mengqingpo.com:8888/fugary/my-test/-/tree/main/docs";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals(GitRepoInfo.Platform.GITLAB, info.getPlatform());
        Assertions.assertEquals("https://git.mengqingpo.com:8888", info.getServerUrl());
        Assertions.assertEquals("fugary/my-test", info.getProjectPath());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("my-test", info.getRepo());
        Assertions.assertEquals("main", info.getBranch());
        Assertions.assertEquals("docs", info.getSubPath());
    }

    @Test
    public void testGitLabNestedSubgroups() {
        String url = "https://gitlab.com/company/team/subteam/doc-center/-/tree/release-1.0/api/v1/specs";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals(GitRepoInfo.Platform.GITLAB, info.getPlatform());
        Assertions.assertEquals("https://gitlab.com", info.getServerUrl());
        Assertions.assertEquals("company/team/subteam/doc-center", info.getProjectPath());
        Assertions.assertEquals("company/team/subteam", info.getOwner());
        Assertions.assertEquals("doc-center", info.getRepo());
        Assertions.assertEquals("release-1.0", info.getBranch());
        Assertions.assertEquals("api/v1/specs", info.getSubPath());
    }

    @Test
    public void testGitHubDeepSubfolder() {
        String url = "https://github.com/fugary/citsgbt-projects/tree/master/NewHRBox/new-hrbox-parent/docs";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals(GitRepoInfo.Platform.GITHUB, info.getPlatform());
        Assertions.assertEquals("https://github.com", info.getServerUrl());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("citsgbt-projects", info.getRepo());
        Assertions.assertEquals("fugary/citsgbt-projects", info.getProjectPath());
        Assertions.assertEquals("master", info.getBranch());
        Assertions.assertEquals("NewHRBox/new-hrbox-parent/docs", info.getSubPath());
    }

    @Test
    public void testGitee() {
        String url = "https://gitee.com/fugary/simple-api-doc/tree/master/docs/guide";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals(GitRepoInfo.Platform.GITEE, info.getPlatform());
        Assertions.assertEquals("https://gitee.com", info.getServerUrl());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("simple-api-doc", info.getRepo());
        Assertions.assertEquals("master", info.getBranch());
        Assertions.assertEquals("docs/guide", info.getSubPath());
    }

    @Test
    public void testNonGitUrlsReturnNull() {
        Assertions.assertNull(GitRepoUrlResolver.resolve("https://example.com/api/swagger.json"));
        Assertions.assertNull(GitRepoUrlResolver.resolve("https://oss.my-company.com/files/docs.zip"));
        Assertions.assertNull(GitRepoUrlResolver.resolve("https://github.com/fugary/simple-api-doc/archive/refs/heads/main.zip"));
        Assertions.assertNull(GitRepoUrlResolver.resolve(""));
        Assertions.assertNull(GitRepoUrlResolver.resolve(null));
    }
}
