package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDocSchema;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
public interface ApiDocSchemaService extends IService<ApiDocSchema> {

    /**
     * 按照doc删除
     *
     * @param docId
     * @return
     */
    boolean deleteByDoc(Integer docId);
}
