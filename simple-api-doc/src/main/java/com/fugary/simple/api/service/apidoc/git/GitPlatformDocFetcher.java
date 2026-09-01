package com.fugary.simple.api.service.apidoc.git;

import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;

/**
 * Git 平台文档抓取策略接口
 *
 * @author gary.fu
 */
public interface GitPlatformDocFetcher {

    /**
     * 是否支持指定平台
     *
     * @param platform 平台类型
     * @return 是否支持
     */
    boolean supports(GitRepoInfo.Platform platform);

    /**
     * 根据 GitRepoInfo 通过 REST API 抓取指定子目录下的 Markdown 文件列表与纯文本
     *
     * @param repoInfo 解析后的 Git 仓库信息
     * @param source 原始带有认证信息的导入参数
     * @return 包含 Virtual JSON 文件列表的 SimpleResult（格式：[{"path":"...","content":"..."}]）
     */
    SimpleResult<DocSourceData> fetchDocs(GitRepoInfo repoInfo, UrlWithAuthVo source);
}
