package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.entity.api.ApiUser;
import com.fugary.simple.api.mapper.api.ApiProjectShareMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
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
