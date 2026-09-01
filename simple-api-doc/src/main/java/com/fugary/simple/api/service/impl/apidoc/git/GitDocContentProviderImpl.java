package com.fugary.simple.api.service.impl.apidoc.git;

import com.fugary.simple.api.contants.SystemErrorConstants;
import com.fugary.simple.api.service.apidoc.git.GitDocContentProvider;
import com.fugary.simple.api.service.apidoc.git.GitPlatformDocFetcher;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Git 仓库目录文档抓取路由提供者（基于策略模式调度各平台 Fetcher）
 *
 * @author gary.fu
 */
@Slf4j
@Service
public class GitDocContentProviderImpl implements GitDocContentProvider {

    private final List<GitPlatformDocFetcher> fetchers;

    @Autowired
    public GitDocContentProviderImpl(List<GitPlatformDocFetcher> fetchers) {
        this.fetchers = fetchers != null ? fetchers : Collections.emptyList();
    }

    @Override
    public SimpleResult<DocSourceData> getContent(GitRepoInfo repoInfo, UrlWithAuthVo source) {
        if (repoInfo == null || repoInfo.getPlatform() == null) {
            return SimpleResultUtils.createSimpleResult(SystemErrorConstants.CODE_2009);
        }

        GitPlatformDocFetcher targetFetcher = null;
        for (GitPlatformDocFetcher fetcher : fetchers) {
            if (fetcher.supports(repoInfo.getPlatform())) {
                targetFetcher = fetcher;
                break;
            }
        }

        if (targetFetcher == null) {
            log.warn("未找到支持的 Git 平台抓取策略: platform={}", repoInfo.getPlatform());
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, "暂不支持的 Git 服务平台: " + repoInfo.getPlatform());
        }

        try {
            return targetFetcher.fetchDocs(repoInfo, source);
        } catch (Exception e) {
            log.error("Git 仓库文档抓取异常: repoInfo={}", repoInfo, e);
            String baseMsg = SimpleResultUtils.getErrorMsg(SystemErrorConstants.CODE_2009);
            return SimpleResultUtils.createError(SystemErrorConstants.CODE_2009, baseMsg + ": " + e.getMessage());
        }
    }
}
