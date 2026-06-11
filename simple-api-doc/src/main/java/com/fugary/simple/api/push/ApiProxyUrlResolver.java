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
        return resolveProjectId(targetUrl, null) != null;
    }

    public Integer resolveProjectId(String targetUrl, Integer projectId) {
        if (StringUtils.isBlank(targetUrl)) {
            return null;
        }
        String shareId = SecurityUtils.getLoginShareId();
        return StringUtils.isNotBlank(shareId)
                ? resolveShareProjectId(shareId, targetUrl, projectId)
                : resolveAdminProjectId(targetUrl, projectId);
    }

    protected Integer resolveAdminProjectId(String targetUrl, Integer projectId) {
        ApiUserVo loginUser = SecurityUtils.getLoginUser();
        return loginUser == null ? null : apiProjectService.list(Wrappers.<ApiProject>query()
                .eq(projectId != null, "id", projectId)
                .isNotNull("env_content")).stream()
                .filter(project -> apiGroupService.checkProjectAccess(loginUser, project, ApiGroupAuthority.READABLE))
                .filter(project -> containsEnvUrl(project.getEnvContent(), targetUrl))
                .map(ApiProject::getId)
                .findFirst().orElse(null);
    }

    protected Integer resolveShareProjectId(String shareId, String targetUrl, Integer projectId) {
        ApiProjectShare apiShare = apiProjectShareService.loadByShareId(shareId);
        if (apiShare == null) {
            return null;
        }
        ApiProject apiProject = apiProjectService.getById(apiShare.getProjectId());
        return apiProject != null && (projectId == null || projectId.equals(apiShare.getProjectId()))
                && containsEnvUrl(apiProject.getEnvContent(), targetUrl)
                && (StringUtils.isBlank(apiShare.getEnvContent()) || containsEnvUrl(apiShare.getEnvContent(), targetUrl))
                ? apiShare.getProjectId() : null;
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
