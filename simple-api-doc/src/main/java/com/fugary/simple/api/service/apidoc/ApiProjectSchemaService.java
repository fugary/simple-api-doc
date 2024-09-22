package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiProjectSchema;

import java.util.List;
import java.util.Map;

/**
 * Created on 2024/7/31 11:42 .<br>
 *
 * @author gary.fu
 */
public interface ApiProjectSchemaService extends IService<ApiProjectSchema> {

    /**
     * 复制时写入schema信息
     *
     * @param schemaMap
     * @param oldRequestId
     * @param oldDataId
     * @param requestId
     * @param dataId
     */
    void saveCopySchemas(Map<String, List<ApiProjectSchema>> schemaMap, Integer oldRequestId, Integer oldDataId,
                         Integer requestId, Integer dataId);

    /**
     * 复制时写入schema信息
     *
     * @param schemas
     * @param requestId
     * @param dataId
     */
    void saveCopySchemas(List<ApiProjectSchema> schemas, Integer requestId, Integer dataId);

    /**
     * 查询Schema列表
     * @param requestId
     * @param dataId
     * @return
     */
    List<ApiProjectSchema> querySchemas(Integer requestId, Integer dataId);
}
