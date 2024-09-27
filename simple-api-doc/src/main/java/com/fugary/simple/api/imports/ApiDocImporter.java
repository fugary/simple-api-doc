package com.fugary.simple.api.imports;

import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;

import java.util.List;

/**
 * 导入工具接口，方便后续处理其他格式导入
 * Create date 2024/7/12<br>
 *
 * @author gary.fu
 */
public interface ApiDocImporter {
    /**
     * 是否支持
     *
     * @param type
     * @return
     */
    boolean isSupport(String type);

    /**
     * 解析数据
     *
     * @param data
     * @return
     */
    ExportApiProjectVo doImport(String data);

    /**
     * 查找可用导入器
     *
     * @param importers 导入器
     * @param type      类型
     * @return
     */
    static ApiDocImporter findImporter(List<ApiDocImporter> importers, String type) {
        if (importers != null) {
            for (ApiDocImporter mockGroupImporter : importers) {
                if (mockGroupImporter.isSupport(type)) {
                    return mockGroupImporter;
                }
            }
        }
        return null;
    }
}
