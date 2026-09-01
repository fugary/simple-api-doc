package com.fugary.simple.api.web.vo.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

/**
 * Git 仓库与目录解析信息（平台无关的标准 Git 模型）
 *
 * @author gary.fu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitRepoInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 站点根 URL，如 https://github.com 或 https://git.mengqingpo.com:8888 */
    private String serverUrl;

    /** 仓库拥有者或组织/Group，如 fugary 或 org/subgroup */
    private String owner;

    /** 仓库名，如 citsgbt-projects 或 my-test */
    private String repo;

    /** 完整项目路径，如 fugary/my-test 或 group/subgroup/project */
    private String projectPath;

    /** 分支名，如 main 或 master */
    private String branch;

    /** 目标子目录路径，如 docs 或 NewHRBox/new-hrbox-parent/docs */
    private String subPath;

    /** 原始输入的 URL */
    private String rawUrl;

    /** Git Clone URL（如 https://github.com/fugary/citsgbt-projects.git） */
    private String cloneUrl;

    /**
     * 获取标准的 Git clone URL
     */
    public String getCloneUrl() {
        if (StringUtils.isNotBlank(cloneUrl)) {
            return cloneUrl;
        }
        if (StringUtils.isNotBlank(serverUrl) && StringUtils.isNotBlank(projectPath)) {
            return serverUrl + "/" + projectPath + ".git";
        }
        return rawUrl;
    }
}
