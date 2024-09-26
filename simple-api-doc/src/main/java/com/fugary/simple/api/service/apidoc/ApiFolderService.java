package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.web.vo.exports.ExportApiDocVo;
import com.fugary.simple.api.web.vo.exports.ExportApiFolderVo;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiFolderService extends IService<ApiFolder> {
    /**
     * 保存数据
     *
     * @param apiFolders
     * @param project
     * @param parentFolder
     * @param extraDocs
     * @return
     */
    int saveApiFolders(List<ExportApiFolderVo> apiFolders, ApiProject project, ApiFolder parentFolder, List<ExportApiDocVo> extraDocs);

    /**
     * 获取根目录
     *
     * @param projectId
     * @return
     */
    ApiFolder getRootFolder(Integer projectId);

    /**
     * 创建根目录
     *
     * @param project
     * @return
     */
    ApiFolder createRootFolder(ApiProject project);

    /**
     * 获取folders列表
     *
     * @param projectId
     * @return
     */
    List<ApiFolder> loadApiFolders(Integer projectId);

    /**
     * 获取folders列表
     *
     * @param projectId
     * @return
     */
    List<ApiFolder> loadEnabledApiFolders(Integer projectId);

    /**
     * 解析成Map结构
     *
     * @param apiFolders
     * @return
     */
    Pair<Map<String, ApiFolder>, Map<Integer, String>> calcFolderMap(List<ApiFolder> apiFolders);

    /**
     * 删除文件夹
     *
     * @param folderId
     * @return
     */
    boolean deleteFolder(Integer folderId);

    /**
     * 按照projectId删除文件夹
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);
}
