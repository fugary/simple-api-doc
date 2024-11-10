package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocHistory;

/**
 * Create date 2024/11/8<br>
 *
 * @author gary.fu
 */
public interface ApiDocHistoryService extends IService<ApiDocHistory> {

    /**
     * 从apiDoc中保存历史版本
     *
     * @param apiDoc
     * @return
     */
    boolean saveByApiDoc(ApiDoc apiDoc);
}
