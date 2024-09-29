package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDocSchema;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiDocSchemaService extends IService<ApiDocSchema> {

    /**
     * 按照doc获取
     * @param docId
     * @return
     */
    List<ApiDocSchema> loadByDoc(Integer docId);

    /**
     * 按照doc删除
     *
     * @param docId
     * @return
     */
    boolean deleteByDoc(Integer docId);
}
