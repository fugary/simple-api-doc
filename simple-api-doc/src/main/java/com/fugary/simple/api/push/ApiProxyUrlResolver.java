package com.fugary.simple.api.push;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.enums.ApiGroupAuthority;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.service.apidoc.ApiGroupService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.exports.ExportEnvConfigVo;
import com.fugary.simple.api.web.vo.user.ApiUserVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Verifies that proxy target URLs come from saved environment configs.
 *
 * Create date 2026/06/11<br>
 *
 * @author gary.fu
 */
@Component
public class ApiProxyUrlResolver {

    private static final TypeReference<List<ExportEnvConfigVo>> ENV_CONFIG_TYPE = new TypeReference<>() {
    };

    @Autowired
    private ApiProjectService apiProjectService;

    @Autowired
    private ApiProjectShareService apiProjectShareService;

    @Autowired
    private ApiGroupService apiGroupService;

    public boolean isAllowedTargetUrl(String targetUrl) {
        if (StringUtils.isBlank(targetUrl)) {
            return false;
        }
        String shareId = SecurityUtils.getLoginShareId();
        return StringUtils.isNotBlank(shareId)
                ? isAllowedShareTargetUrl(shareId, targetUrl)
                : isAllowedAdminTargetUrl(targetUrl);
    }

    protected boolean isAllowedAdminTargetUrl(String targetUrl) {
        ApiUserVo loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            return false;
        }
        List<ApiProject> projects = apiProjectService.list(Wrappers.<ApiProject>query()
                .isNotNull("env_content"));
        return projects.stream()
                .filter(project -> apiGroupService.checkProjectAccess(loginUser, project, ApiGroupAuthority.READABLE))
                .anyMatch(project -> containsEnvUrl(project.getEnvContent(), targetUrl));
    }

    protected boolean isAllowedShareTargetUrl(String shareId, String targetUrl) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        if (apiShare == null) {
            return false;
        }
        ApiProject apiProject = apiProjectService.getById(apiShare.getProjectId());
        if (apiProject == null || !containsEnvUrl(apiProject.getEnvContent(), targetUrl)) {
            return false;
        }
        return StringUtils.isBlank(apiShare.getEnvContent())
                || containsEnvUrl(apiShare.getEnvContent(), targetUrl);
    }

    protected boolean containsEnvUrl(String envContent, String targetUrl) {
        String normalizedTargetUrl = normalizeEnvUrl(targetUrl);
        return parseEnvConfigs(envContent).stream()
                .filter(env -> env != null && !Boolean.TRUE.equals(env.getDisabled()))
                .map(ExportEnvConfigVo::getUrl)
                .filter(StringUtils::isNotBlank)
                .anyMatch(url -> StringUtils.equals(normalizeEnvUrl(url), normalizedTargetUrl));
    }

    protected List<ExportEnvConfigVo> parseEnvConfigs(String envContent) {
        if (StringUtils.isBlank(envContent)) {
            return Collections.emptyList();
        }
        return Objects.requireNonNullElse(JsonUtils.fromJson(envContent, ENV_CONFIG_TYPE), Collections.emptyList());
    }

    protected String normalizeEnvUrl(String url) {
        return StringUtils.stripEnd(StringUtils.trimToEmpty(url), "/");
    }
}
