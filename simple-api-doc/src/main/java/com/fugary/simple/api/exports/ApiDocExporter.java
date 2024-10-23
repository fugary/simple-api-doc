package com.fugary.simple.api.exports;

import java.util.List;

/**
 * Create date 2024/10/23<br>
 *
 * @author gary.fu
 */
public interface ApiDocExporter<T> {

    /**
     * 数据导出
     *
     * @param projectId
     * @param docIds
     * @return
     */
    T export(Integer projectId, List<Integer> docIds);
}
