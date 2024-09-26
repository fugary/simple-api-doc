package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectSchema;
import com.fugary.simple.api.web.vo.exports.ExportApiProjectSchemaVo;

/**
 * Created on 2024/7/31 11:42 .<br>
 *
 * @author gary.fu
 */
public interface ApiProjectSchemaService extends IService<ApiProjectSchema> {

    /**
     * 按照projectId查找
     *
     * @param projectId
     * @return
     */
    ApiProjectSchema loadByProjectId(Integer projectId);

    /**
     * 保存导入数据
     *
     * @param projectSchemaVo
     */
    void saveApiProjectSchema(ExportApiProjectSchemaVo projectSchemaVo, ApiProject apiProject, boolean importExists);

    /**
     * 按照projectId删除
     *
     * @param projectId
     * @return
     */
    boolean deleteByProject(Integer projectId);
}
