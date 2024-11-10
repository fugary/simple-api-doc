package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocHistory;

import java.util.List;

/**
 * Create date 2024/11/8<br>
 *
 * @author gary.fu
 */
public interface ApiDocHistoryService extends IService<ApiDocHistory> {

    /**
     * 加载历史版本
     *
     * @param docId
     * @return
     */
    List<ApiDocHistory> loadByDoc(Integer docId, Integer limit);

    /**
     * 从apiDoc中保存历史版本
     *
     * @param apiDoc
     * @return
     */
    boolean saveByApiDoc(ApiDoc apiDoc);
}