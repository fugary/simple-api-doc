package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.mapper.api.ApiDocSchemaMapper;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiDocSchemaServiceImpl extends ServiceImpl<ApiDocSchemaMapper, ApiDocSchema> implements ApiDocSchemaService {

    @Override
    public List<ApiDocSchema> loadByDoc(Integer docId) {
        return this.list(Wrappers.<ApiDocSchema>query().eq("doc_id", docId));
    }

    @Override
    public ApiDocDetailVo loadDetailVo(ApiDoc apiDoc) {
        ApiDocDetailVo apiDocVo = new ApiDocDetailVo();
        BeanUtils.copyProperties(apiDoc, apiDocVo);
        List<ApiDocSchema> docSchemas = loadByDoc(apiDoc.getId());
        docSchemas.forEach(schema -> {
            switch (schema.getBodyType()) {
                case ApiDocConstants.DOC_SCHEMA_TYPE_REQUEST:
                    apiDocVo.getRequestsSchemas().add(schema);
                    break;
                case ApiDocConstants.DOC_SCHEMA_TYPE_RESPONSE:
                    apiDocVo.getResponsesSchemas().add(schema);
                    break;
                case ApiDocConstants.DOC_SCHEMA_TYPE_PARAMETERS:
                    apiDocVo.setParametersSchema(schema);
                    break;
            }
        });
        return apiDocVo;
    }

    @Override
    public boolean deleteByDoc(Integer docId) {
        return this.remove(Wrappers.<ApiDocSchema>query().eq("doc_id", docId));
    }
}
