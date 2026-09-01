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
        Assertions.assertEquals("https://git.mengqingpo.com:8888", info.getServerUrl());
        Assertions.assertEquals("fugary/my-test", info.getProjectPath());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("my-test", info.getRepo());
        Assertions.assertEquals("main", info.getBranch());
        Assertions.assertEquals("docs", info.getSubPath());
        Assertions.assertEquals("https://git.mengqingpo.com:8888/fugary/my-test.git", info.getCloneUrl());
    }

    @Test
    public void testGitLabNestedSubgroups() {
        String url = "https://gitlab.com/company/team/subteam/doc-center/-/tree/release-1.0/api/v1/specs";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals("https://gitlab.com", info.getServerUrl());
        Assertions.assertEquals("company/team/subteam/doc-center", info.getProjectPath());
        Assertions.assertEquals("company/team/subteam", info.getOwner());
        Assertions.assertEquals("doc-center", info.getRepo());
        Assertions.assertEquals("release-1.0", info.getBranch());
        Assertions.assertEquals("api/v1/specs", info.getSubPath());
        Assertions.assertEquals("https://gitlab.com/company/team/subteam/doc-center.git", info.getCloneUrl());
    }

    @Test
    public void testGitHubDeepSubfolder() {
        String url = "https://github.com/fugary/citsgbt-projects/tree/master/NewHRBox/new-hrbox-parent/docs";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals("https://github.com", info.getServerUrl());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("citsgbt-projects", info.getRepo());
        Assertions.assertEquals("fugary/citsgbt-projects", info.getProjectPath());
        Assertions.assertEquals("master", info.getBranch());
        Assertions.assertEquals("NewHRBox/new-hrbox-parent/docs", info.getSubPath());
        Assertions.assertEquals("https://github.com/fugary/citsgbt-projects.git", info.getCloneUrl());
    }

    @Test
    public void testGitee() {
        String url = "https://gitee.com/fugary/simple-api-doc/tree/master/docs/guide";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals("https://gitee.com", info.getServerUrl());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("simple-api-doc", info.getRepo());
        Assertions.assertEquals("master", info.getBranch());
        Assertions.assertEquals("docs/guide", info.getSubPath());
        Assertions.assertEquals("https://gitee.com/fugary/simple-api-doc.git", info.getCloneUrl());
    }

    @Test
    public void testGitCode() {
        String url = "https://gitcode.com/fugary/simple-doc/tree/main/docs/api";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals("https://gitcode.com", info.getServerUrl());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("simple-doc", info.getRepo());
        Assertions.assertEquals("main", info.getBranch());
        Assertions.assertEquals("docs/api", info.getSubPath());
        Assertions.assertEquals("https://gitcode.com/fugary/simple-doc.git", info.getCloneUrl());
    }

    @Test
    public void testGitea() {
        String url = "https://gitea.example.com:3000/dev-team/specs/src/branch/v2.0/markdown/guide";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals("https://gitea.example.com:3000", info.getServerUrl());
        Assertions.assertEquals("dev-team", info.getOwner());
        Assertions.assertEquals("specs", info.getRepo());
        Assertions.assertEquals("v2.0", info.getBranch());
        Assertions.assertEquals("markdown/guide", info.getSubPath());
        Assertions.assertEquals("https://gitea.example.com:3000/dev-team/specs.git", info.getCloneUrl());
    }

    @Test
    public void testDirectGitCloneUrl() {
        String url = "https://github.com/fugary/citsgbt-projects.git";
        GitRepoInfo info = GitRepoUrlResolver.resolve(url);

        Assertions.assertNotNull(info);
        Assertions.assertEquals("https://github.com", info.getServerUrl());
        Assertions.assertEquals("fugary", info.getOwner());
        Assertions.assertEquals("citsgbt-projects", info.getRepo());
        Assertions.assertEquals("main", info.getBranch());
        Assertions.assertEquals("", info.getSubPath());
        Assertions.assertEquals("https://github.com/fugary/citsgbt-projects.git", info.getCloneUrl());
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
