package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocHistory;
import com.fugary.simple.api.mapper.api.ApiDocHistoryMapper;
import com.fugary.simple.api.service.apidoc.ApiDocHistoryService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Create date 2024/11/8<br>
 *
 * @author gary.fu
 */
@Service
public class ApiDocHistoryServiceImpl extends ServiceImpl<ApiDocHistoryMapper, ApiDocHistory> implements ApiDocHistoryService {

    @Override
    public List<ApiDocHistory> loadByDoc(Integer docId, Integer limit) {
        return this.list(Wrappers.<ApiDocHistory>query().eq("doc_id", docId)
                .orderByDesc("create_time").last("limit " + limit));
    }

    @Override
    public boolean saveByApiDoc(ApiDoc apiDoc) {
        if (apiDoc != null && ApiDocConstants.DOC_TYPE_MD.equals(apiDoc.getDocType())) {
            ApiDocHistory apiDocHistory = SimpleModelUtils.copy(apiDoc, ApiDocHistory.class);
            apiDocHistory.setId(null);
            apiDocHistory.setDocId(apiDoc.getId());
            apiDocHistory.setCreator(null);
            apiDocHistory.setCreateDate(null);
            SimpleModelUtils.addAuditInfo(apiDocHistory);
            return this.save(apiDocHistory);
        }
        return true;
    }
}