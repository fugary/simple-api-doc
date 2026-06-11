package com.fugary.simple.api.push;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiProxyUrlResolverTest {

    @Mock
    private ApiProjectService apiProjectService;

    @Mock
    private ApiProjectShareService apiProjectShareService;

    @Mock
    private ApiGroupService apiGroupService;

    @InjectMocks
    private ApiProxyUrlResolver resolver;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void adminProxyOnlyAllowsReadableProjectEnvUrls() {
        loginAs(2, "alice");
        ApiProject readableProject = project(1, envContent(
                env("dev", "https://api.example.com"),
                disabledEnv("disabled", "https://disabled.example.com")));
        ApiProject deniedProject = project(2, envContent(env("denied", "https://denied.example.com")));
        when(apiProjectService.list(any(Wrapper.class))).thenReturn(List.of(readableProject, deniedProject));
        when(apiGroupService.checkProjectAccess(any(), eq(readableProject), eq(ApiGroupAuthority.READABLE))).thenReturn(true);
        when(apiGroupService.checkProjectAccess(any(), eq(deniedProject), eq(ApiGroupAuthority.READABLE))).thenReturn(false);

        assertThat(resolver.isAllowedTargetUrl("https://api.example.com/")).isTrue();
        assertThat(resolver.isAllowedTargetUrl("https://disabled.example.com")).isFalse();
        assertThat(resolver.isAllowedTargetUrl("https://denied.example.com")).isFalse();
        assertThat(resolver.isAllowedTargetUrl("https://other.example.com")).isFalse();
    }

    @Test
    void shareProxyUsesProjectEnvAndOptionalShareEnvLimit() {
        shareAs("share-1");
        ApiProject project = project(1, envContent(
                env("dev", "https://dev.example.com"),
                env("prod", "https://prod.example.com")));
        ApiProjectShare share = share(1, "share-1", envContent(env("dev", "https://dev.example.com")));
        when(apiProjectShareService.loadByShareId("share-1")).thenReturn(share);
        when(apiProjectService.getById(1)).thenReturn(project);

        assertThat(resolver.isAllowedTargetUrl("https://dev.example.com/")).isTrue();
        assertThat(resolver.isAllowedTargetUrl("https://prod.example.com")).isFalse();
    }

    @Test
    void shareProxyAllowsAllProjectEnvsWhenShareDoesNotLimitEnvs() {
        shareAs("share-1");
        ApiProject project = project(1, envContent(env("prod", "https://prod.example.com")));
        ApiProjectShare share = share(1, "share-1", null);
        when(apiProjectShareService.loadByShareId("share-1")).thenReturn(share);
        when(apiProjectService.getById(1)).thenReturn(project);

        assertThat(resolver.isAllowedTargetUrl("https://prod.example.com/")).isTrue();
    }

    private MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        return request;
    }

    private void loginAs(Integer id, String userName) {
        ApiUserVo loginUser = new ApiUserVo();
        loginUser.setId(id);
        loginUser.setUserName(userName);
        MockHttpServletRequest request = request("/admin/proxy/users");
        request.setAttribute(ApiDocConstants.API_USER_KEY, loginUser);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void shareAs(String shareId) {
        MockHttpServletRequest request = request("/shares/proxy/users");
        request.setAttribute(ApiDocConstants.AUTHORIZED_SHARED_KEY, shareId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ApiProject project(Integer id, String envContent) {
        ApiProject project = new ApiProject();
        project.setId(id);
        project.setEnvContent(envContent);
        return project;
    }

    private ApiProjectShare share(Integer projectId, String shareId, String envContent) {
        ApiProjectShare share = new ApiProjectShare();
        share.setProjectId(projectId);
        share.setShareId(shareId);
        share.setEnvContent(envContent);
        return share;
    }

    private String envContent(ExportEnvConfigVo... envConfigs) {
        return JsonUtils.toJson(List.of(envConfigs));
    }

    private ExportEnvConfigVo env(String name, String url) {
        return new ExportEnvConfigVo(name, url, null, false);
    }

    private ExportEnvConfigVo disabledEnv(String name, String url) {
        return new ExportEnvConfigVo(name, url, null, true);
    }
}
