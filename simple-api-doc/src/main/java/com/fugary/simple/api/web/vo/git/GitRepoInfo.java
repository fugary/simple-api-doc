package com.fugary.simple.api.web.vo.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Git 仓库与目录解析信息
 *
 * @author gary.fu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitRepoInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Platform {
        GITHUB,
        GITLAB,
        GITEE,
        OTHER
    }

    /** Git 服务平台类型 (GITHUB, GITLAB, GITEE) */
    private Platform platform;

    /** 站点根 URL，如 https://github.com 或 https://git.mengqingpo.com:8888 */
    private String serverUrl;

    /** 仓库拥有者或组织/Group，如 fugary 或 org/subgroup */
    private String owner;

    /** 仓库名，如 citsgbt-projects 或 my-test */
    private String repo;

    /** 完整项目路径（GitLab 场景），如 fugary/my-test 或 group/subgroup/project */
    private String projectPath;

    /** 分支名，如 main 或 master */
    private String branch;

    /** 目标子目录路径，如 docs 或 NewHRBox/new-hrbox-parent/docs */
    private String subPath;

    /** 原始输入的 URL */
    private String rawUrl;
}
