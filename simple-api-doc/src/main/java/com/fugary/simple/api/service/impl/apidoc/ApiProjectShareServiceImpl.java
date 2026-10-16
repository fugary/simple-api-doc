package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.AiConfig;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.mapper.api.ApiProjectShareMapper;
import com.fugary.simple.api.exception.SimpleRuntimeException;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import com.fugary.simple.api.service.ai.AiConfigService;
import com.fugary.simple.api.service.apidoc.ApiProjectService;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.service.token.TokenService;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectShareServiceImpl extends ServiceImpl<ApiProjectShareMapper, ApiProjectShare> implements ApiProjectShareService {

    @Lazy
    @Autowired
    private TokenService tokenService;

    @Lazy
    @Autowired
    private ApiProjectService projectService;

    @Lazy
    @Autowired
    private ApiDocService docService;

    @Autowired
    private AiConfigService aiConfigService;

    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectShare>query().eq("project_id", projectId));
    }

    @Override
    public ApiProjectShare loadByShareId(String shareId) {
        return this.getOne(Wrappers.<ApiProjectShare>query().eq("share_id", shareId)
                .eq("status", ApiDocConstants.STATUS_ENABLED));
    }

    @Override
    public ApiProjectShare loadCurrentShare() {
        String shareId = SecurityUtils.getLoginShareId();
        if (StringUtils.isBlank(shareId)) return null;
        ApiProjectShare share = loadByShareId(shareId);
        if (share == null || (share.getExpireDate() != null && !share.getExpireDate().after(new Date()))) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403, "分享已失效");
        }
        return share;
    }

    @Override
    public boolean canReadDoc(ApiDoc doc, ApiProjectShare share) {
        if (doc == null || share == null || doc.getModifyFrom() != null
                || !Objects.equals(doc.getProjectId(), share.getProjectId())
                || !ApiDocConstants.STATUS_ENABLED.equals(doc.getStatus())
                || (!ApiDocConstants.DOC_TYPE_API.equals(doc.getDocType())
                && !ApiDocConstants.DOC_TYPE_MD.equals(doc.getDocType()))) return false;
        Set<Integer> ids = SimpleModelUtils.getShareDocIds(share.getShareDocs());
        if (!ids.isEmpty() && !ids.contains(doc.getId())) return false;
        ApiProject project = projectService.getById(share.getProjectId());
        return project != null && ApiDocConstants.STATUS_ENABLED.equals(project.getStatus());
    }

    @Override
    public ApiDoc requireDoc(String docId, ApiProjectShare share) {
        int id = NumberUtils.toInt(docId, 0);
        ApiDoc doc = id > 0 ? docService.getById(id) : null;
        if (!canReadDoc(doc, share)) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403, "当前分享无权访问此文档");
        }
        return doc;
    }

    @Override
    public AiConfig requireAiConfig(ApiProjectShare share, boolean search) {
        if (!Boolean.TRUE.equals(search ? share.getAiSearchEnabled() : share.getAiSampleEnabled())) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_403, "当前分享未开启此 AI 功能");
        }
        AiConfig config = aiConfigService.getAvailableDefaultAiConfig();
        if (config == null) {
            throw new SimpleRuntimeException(SystemErrorConstants.CODE_2014, "默认 AI 模型不可用");
        }
        return config;
    }

    @Override
    public int copyProjectShares(Integer fromProjectId, Integer toProjectId, Integer id, Map<Integer, Integer> docMappings) {
        List<ApiProjectShare> shares = list(Wrappers.<ApiProjectShare>query()
                .eq("project_id", fromProjectId)
                .eq(id != null, "id", id));
        shares.forEach(share -> {
            share.setId(null);
            share.setShareId(SimpleModelUtils.uuid());
            share.setProjectId(toProjectId);
            if (fromProjectId.equals(toProjectId)) {
                share.setShareName(share.getShareName() + ApiDocConstants.COPY_SUFFIX);
            }
            if (StringUtils.isNotBlank(share.getShareDocs())) {
                // 复制文档ID
                String shareDocs = share.getShareDocs();
                share.setShareDocs(null);
                Set<Integer> docIds = JsonUtils.fromJson(shareDocs, new TypeReference<>() {
                });
                if (CollectionUtils.isNotEmpty(docIds)) {
                    List<Integer> newDocIds = docIds.stream().map(docId -> docMappings.getOrDefault(docId, null))
                            .filter(Objects::nonNull).collect(Collectors.toList());
                    if (CollectionUtils.isNotEmpty(newDocIds)) {
                        share.setShareDocs(JsonUtils.toJson(newDocIds));
                    }
                }
            }
            SimpleModelUtils.cleanCreateModel(share);
            save(share);
        });
        return shares.size();
    }

    @Override
    public SimpleResult<ApiProjectShare> copyProjectShare(ApiProjectShare share) {
        SimpleModelUtils.cleanCreateModel(share);
        share.setShareId(SimpleModelUtils.uuid());
        share.setShareName(share.getShareName() + ApiDocConstants.COPY_SUFFIX);
        if (StringUtils.isNotBlank(share.getSharePassword())) {
            share.setSharePassword(RandomStringUtils.random(8, "abcdefghijklmnopqrstuvwxyz0123456789"));
        }
        save(share);
        return SimpleResultUtils.createSimpleResult(share);
    }

    @Override
    public SimpleResult<ApiUser> validateSharePwd(String password, ApiProjectShare apiShare) {
        if (StringUtils.isNotBlank(password)) {
            if (apiShare != null && StringUtils.equals(apiShare.getSharePassword(), password)) {
                return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_0);
            }
            SimpleResult<ApiUser> userResult = tokenService.validate(password); // JWT验证
            if (userResult.isSuccess()) {
                String shareId = SecurityUtils.getUserShareId(userResult.getResultData());
                if (apiShare == null) { // 拦截器中加载ProjectShare
                    apiShare = loadByShareId(shareId);
                }
                if (SecurityUtils.validateShareUserName(userResult.getResultData().getUserName(), apiShare)) {
                    return userResult;
                }
            }
        }
        return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_401);
    }
}
