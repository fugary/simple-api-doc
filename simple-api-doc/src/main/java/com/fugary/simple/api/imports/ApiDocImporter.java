package com.fugary.simple.api.imports;

import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import org.springframework.context.i18n.LocaleContextHolder;

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
     * 解析数据（携带导入参数，如文件名）
     *
     * @param data
     * @param importVo
     * @return
     */
    default ExportApiProjectVo doImport(String data, ApiProjectImportVo importVo) {
        return doImport(data);
    }

    /**
     * 获取导入器类型
     *
     * @return
     */
    default String getType() {
        return "";
    }

    /**
     * 获取导入器类型显示名称
     *
     * @return
     */
    default String getTypeName() {
        return SimpleResultUtils.getErrorMsg("simple.api.import.type." + getType(), null, LocaleContextHolder.getLocale());
    }

    /**
     * 获取指定类型的显示名称
     *
     * @param importers 导入器列表
     * @param type      类型
     * @return
     */
    static String getTypeName(List<ApiDocImporter> importers, String type) {
        ApiDocImporter importer = findImporter(importers, type);
        if (importer != null) {
            return importer.getTypeName();
        }
        return SimpleResultUtils.getErrorMsg("simple.api.import.type." + type, null, LocaleContextHolder.getLocale());
    }

    /**
     * 快速特征指纹匹配，判断数据是否符合该导入器格式
     *
     * @param data 导入文本数据
     * @return 是否匹配
     */
    default boolean match(String data) {
        return false;
    }

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

    /**
     * 自动探测数据最匹配的导入器
     *
     * @param importers 导入器列表
     * @param data      导入文本数据
     * @return 匹配到的导入器，若未匹配返回 null
     */
    static ApiDocImporter detectImporter(List<ApiDocImporter> importers, String data) {
        if (importers == null || data == null) {
            return null;
        }
        return importers.stream()
                .filter(importer -> importer.match(data))
                .findFirst()
                .orElse(null);
    }
}
