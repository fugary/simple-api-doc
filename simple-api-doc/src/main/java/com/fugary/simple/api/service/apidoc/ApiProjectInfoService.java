package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiFolder;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectInfo;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectInfoVo;

/**
 * Created on 2024/7/31 11:42 .<br>
 *
 * @author gary.fu
 */
public interface ApiProjectInfoService extends IService<ApiProjectInfo> {

    /**
     * 按照projectId查找
     *
     * @param projectId
     * @param folderId
     * @return
     */
    ApiProjectInfo loadByProjectId(Integer projectId, Integer folderId);

    /**
     * 保存导入数据
     *
     * @param projectInfoVo 导入项目信息
     * @param apiProject 已保存项目基本信息
     * @param mountFolder 挂载的目录
     * @param importExists 是否已经存在该项目
     */
    ApiProjectInfo saveApiProjectInfo(ExportApiProjectInfoVo projectInfoVo, ApiProject apiProject, ApiFolder mountFolder, boolean importExists);

    /**
     * 按照projectId删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);
}
