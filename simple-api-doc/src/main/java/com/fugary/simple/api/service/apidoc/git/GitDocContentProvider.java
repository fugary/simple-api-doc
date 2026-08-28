package com.fugary.simple.api.service.apidoc.git;

import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.git.GitRepoInfo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import com.fugary.simple.api.web.vo.imports.UrlWithAuthVo;

/**
 * Git 仓库目录文档抓取提供者
 *
 * @author gary.fu
 */
public interface GitDocContentProvider {

    /**
     * 根据 GitRepoInfo 通过 REST API 抓取指定子目录下的 Markdown 文件列表与纯文本
     *
     * @param repoInfo 解析后的 Git 仓库信息
     * @param source 原始带有认证信息的导入参数
     * @return 包含 Virtual JSON 文件列表的 SimpleResult（格式：[{"path":"...","content":"..."}]）
     */
    SimpleResult<DocSourceData> getContent(GitRepoInfo repoInfo, UrlWithAuthVo source);
}
