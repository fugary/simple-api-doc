package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocHistory;
import com.fugary.simple.api.mapper.api.ApiDocHistoryMapper;
import com.fugary.simple.api.service.apidoc.ApiDocHistoryService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.springframework.stereotype.Service;

/**
 * Create date 2024/11/8<br>
 *
 * @author gary.fu
 */
@Service
public class ApiDocHistoryServiceImpl extends ServiceImpl<ApiDocHistoryMapper, ApiDocHistory> implements ApiDocHistoryService {

    @Override
    public boolean saveByApiDoc(ApiDoc apiDoc) {
        if (apiDoc != null) {
            ApiDocHistory apiDocHistory = SimpleModelUtils.copy(apiDoc, ApiDocHistory.class);
            apiDocHistory.setId(null);
            apiDocHistory.setDocId(apiDoc.getId());
            apiDocHistory.setCreator(apiDoc.getModifier());
            apiDocHistory.setCreateDate(apiDoc.getModifyDate());
            return this.save(apiDocHistory);
        }
        return true;
    }

    @Override
    public ApiDoc copyFromHistory(ApiDocHistory apiDocHistory, ApiDoc apiDoc) {
        ApiDoc resultDoc = SimpleModelUtils.copy(apiDoc, ApiDoc.class);
        SimpleModelUtils.copy(apiDocHistory, resultDoc);
        resultDoc.setId(apiDoc.getId()); // 还原不能修改的属性
        resultDoc.setDocVersion(apiDoc.getDocVersion());
        return resultDoc;
    }
}
