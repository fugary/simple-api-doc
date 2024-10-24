package com.fugary.simple.api.utils.exports;

import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Create date 2024/9/27<br>
 *
 * @author gary.fu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiDocParseUtils {
    /**
     * 以已存在文件夹为基准计算文件夹层级
     *
     * @param existsFolders 已解析的文件夹列表
     * @param folderPath    文件夹路径 a/b/c/d
     * @return left——底层目录，right——顶层目录
     */
    public static Pair<ExportApiFolderVo, ExportApiFolderVo> calcApiPathFolder(List<ExportApiFolderVo> existsFolders, String folderPath) {
        Map<String, ExportApiFolderVo> folderMap = existsFolders.stream().collect(Collectors.toMap(ExportApiFolderVo::getFolderPath, Function.identity()));
        String[] folderNames = StringUtils.split(folderPath, ApiDocConstants.FOLDER_PATH_SEPARATOR);
        ExportApiFolderVo topFolder = null;
        ExportApiFolderVo currentParentFolder = null;
        for (int i = 0; i < folderNames.length; i++) {
            String folderName = folderNames[i];
            List<String> namePaths = new ArrayList<>();
            for (int j = 0; j <= i; j++) {
                namePaths.add(folderNames[j]);
            }
            String childFolderPath = StringUtils.join(namePaths, ApiDocConstants.FOLDER_PATH_SEPARATOR);
            ExportApiFolderVo childFolder = folderMap.computeIfAbsent(childFolderPath, k -> {
                ExportApiFolderVo folder = new ExportApiFolderVo();
                folder.setFolderPath(childFolderPath);
                folder.setFolderName(folderName);
                folder.setStatus(ApiDocConstants.STATUS_ENABLED);
                return folder;
            });
            if (currentParentFolder != null) {
                addFolderIfNotExist(currentParentFolder.getFolders(), childFolder);
                if (childFolder.getParentFolder() == null) {
                    childFolder.setParentFolder(currentParentFolder);
                }
            }
            addFolderIfNotExist(existsFolders, childFolder);
            currentParentFolder = childFolder; // 把当前folder记为parent
            if (i == 0) {
                topFolder = childFolder;
            }
        }
        // 新增或获取Folder信息
        return Pair.of(currentParentFolder, topFolder); // 应该永远有个folder，不能为空
    }

    /**
     * 是否存在判断
     *
     * @param folders
     * @param folder
     */
    public static void addFolderIfNotExist(List<ExportApiFolderVo> folders, ExportApiFolderVo folder) {
        if (folder != null && folders.stream().noneMatch(cFolder -> StringUtils.equals(cFolder.getFolderPath(), folder.getFolderPath()))) {
            if (folder.getSortId() == null) {
                int size = folders.size();
                folder.setSortId(size * 100 + 10);
            }
            folders.add(folder);
        }
    }
}
