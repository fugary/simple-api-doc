package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

/**
 * Created on 2024/7/31 11:42 .<br>
 *
 * @author gary.fu
 */
public interface ApiProjectInfoService extends IService<ApiProjectInfo> {

    /**
     * 加载详细信息
     *
     * @param projectId
     * @return
     */
    List<ApiProjectInfo> loadByProjectId(Integer projectId);

    /**
     * 按照projectId查找
     *
     * @param projectId
     * @param folderId
     * @return
     */
    ApiProjectInfo loadByProjectId(Integer projectId, Integer folderId);

    /**
     * 按照folderId查找
     *
     * @param folderId
     * @return
     */
    ApiProjectInfo loadByFolderId(Integer folderId);

    /**
     * 保存导入数据
     *
     * @param projectInfoVo 导入项目信息
     * @param apiProject    已保存项目基本信息
     * @param mountFolder   挂载的目录
     * @param importExists  是否已经存在该项目
     */
    ApiProjectInfo saveApiProjectInfo(ExportApiProjectInfoVo projectInfoVo, ApiProject apiProject, ApiFolder mountFolder, boolean importExists);

    /**
     * 按照projectId删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);

    /**
     * 按照folderId删除
     *
     * @param folderId
     * @return
     */
    boolean deleteByFolder(Integer folderId);

    /**
     * 复制info信息
     *
     * @param fromProjectId
     * @param toProjectId
     * @param foldersMap
     * @return
     */
    Map<Integer, Pair<ApiProjectInfo, ApiProjectInfo>> copyProjectInfos(Integer fromProjectId, Integer toProjectId,
                                                                        Map<Integer, Pair<ApiFolder, ApiFolder>> foldersMap);
}
