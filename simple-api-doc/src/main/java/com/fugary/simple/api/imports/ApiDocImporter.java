package com.fugary.simple.api.imports;

import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectVo;
import com.fugary.simple.api.web.vo.imports.ApiProjectImportVo;
import com.fugary.simple.api.web.vo.imports.DocSourceData;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;

/**
 * 导入工具接口，统一以 DocSourceData 作为数据载体
 * Create date 2024/7/12<br>
 *
 * @author gary.fu
 */
public interface ApiDocImporter {

    /**
     * 是否支持指定来源类型
     *
     * @param type
     * @return
     */
    boolean isSupport(String type);

    /**
     * 快速特征指纹匹配，判断数据是否符合该导入器格式
     *
     * @param sourceData 导入来源数据载体
     * @return 是否匹配
     */
    boolean match(DocSourceData sourceData);

    /**
     * 快速特征指纹匹配（字符串便捷重载）
     *
     * @param data 导入文本数据
     * @return 是否匹配
     */
    default boolean match(String data) {
        return match(DocSourceData.ofText(data));
    }

    /**
     * 解析数据（核心方法）
     *
     * @param sourceData 导入来源数据载体
     * @param importVo   导入参数
     * @return 解析后的工程对象
     */
    ExportApiProjectVo doImport(DocSourceData sourceData, ApiProjectImportVo importVo);

    /**
     * 解析数据（便捷重载）
     *
     * @param sourceData 导入来源数据载体
     * @return 解析后的工程对象
     */
    default ExportApiProjectVo doImport(DocSourceData sourceData) {
        return doImport(sourceData, null);
    }

    /**
     * 解析数据（纯文本便捷重载）
     *
     * @param data 纯文本数据
     * @return 解析后的工程对象
     */
    default ExportApiProjectVo doImport(String data) {
        return doImport(DocSourceData.ofText(data), null);
    }

    /**
     * 解析数据（纯文本与导入参数便捷重载）
     *
     * @param data     纯文本数据
     * @param importVo 导入参数
     * @return 解析后的工程对象
     */
    default ExportApiProjectVo doImport(String data, ApiProjectImportVo importVo) {
        return doImport(DocSourceData.ofText(data), importVo);
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
     * @param importers  导入器列表
     * @param sourceData 导入来源数据载体
     * @return 匹配到的导入器，若未匹配返回 null
     */
    static ApiDocImporter detectImporter(List<ApiDocImporter> importers, DocSourceData sourceData) {
        if (importers == null || sourceData == null) {
            return null;
        }
        return importers.stream()
                .filter(importer -> importer.match(sourceData))
                .findFirst()
                .orElse(null);
    }

    /**
     * 自动探测数据最匹配的导入器（字符串便捷重载）
     *
     * @param importers 导入器列表
     * @param data      导入文本数据
     * @return 匹配到的导入器，若未匹配返回 null
     */
    static ApiDocImporter detectImporter(List<ApiDocImporter> importers, String data) {
        if (importers == null || data == null) {
            return null;
        }
        return detectImporter(importers, DocSourceData.ofText(data));
    }
}
