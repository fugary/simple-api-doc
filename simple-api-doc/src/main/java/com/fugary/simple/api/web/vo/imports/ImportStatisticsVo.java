package com.fugary.simple.api.web.vo.imports;

import lombok.Data;

import java.io.Serializable;

/**
 * 导入变更统计 VO
 *
 * @author gary.fu
 */
@Data
public class ImportStatisticsVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新增文档/接口数
     */
    private int docAdded;

    /**
     * 更新文档/接口数
     */
    private int docUpdated;

    /**
     * 未变更文档/接口数
     */
    private int docUnchanged;

    /**
     * 锁定跳过文档数
     */
    private int docLocked;

    /**
     * 新增目录数
     */
    private int folderAdded;

    /**
     * 总耗时 (毫秒)
     */
    private long costTime;

    /**
     * 获取处理的文档总数
     */
    public int getTotalDocs() {
        return docAdded + docUpdated + docUnchanged + docLocked;
    }

    public void addDocAdded() {
        this.docAdded++;
    }

    public void addDocUpdated() {
        this.docUpdated++;
    }

    public void addDocUnchanged() {
        this.docUnchanged++;
    }

    public void addDocLocked() {
        this.docLocked++;
    }

    public void addFolderAdded() {
        this.folderAdded++;
    }

    /**
     * 转换为紧凑格式的摘要字符串，如: "+2 ~5 =12"
     */
    public String toCompactSummary() {
        return String.format("+%d ~%d =%d", docAdded, docUpdated, docUnchanged);
    }
}
