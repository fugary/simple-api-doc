package com.fugary.simple.api.exports;

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
     * @param exportFilter
     * @return
     */
    T export(Integer projectId, ApiExportFilter exportFilter);
}
