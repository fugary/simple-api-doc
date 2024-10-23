package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.mapper.api.ApiDocSchemaMapper;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import com.fugary.simple.api.web.vo.project.ApiDocDetailVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        SimpleModelUtils.copy(apiDoc, apiDocVo);
        List<ApiDocSchema> docSchemas = loadByDoc(apiDoc.getId());
        processDocSchemas(docSchemas, apiDocVo);
        return apiDocVo;
    }

    /**
     * 处理docSchemas
     * @param docSchemas
     * @param apiDocVo
     */
    protected void processDocSchemas(List<ApiDocSchema> docSchemas, ApiDocDetailVo apiDocVo) {
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
    }

    @Override
    public List<ApiDocDetailVo> loadDetailList(List<ApiDoc> apiDocs) {
        List<Integer> docIds = apiDocs.stream().map(ApiDoc::getId).collect(Collectors.toList());
        Map<Integer, List<ApiDocSchema>> schemaMap = this.list(Wrappers.<ApiDocSchema>query().in("doc_id", docIds)).stream()
                .collect(Collectors.groupingBy(ApiDocSchema::getDocId));
        return apiDocs.stream().map(apiDoc -> {
            List<ApiDocSchema> docSchemas = schemaMap.get(apiDoc.getId());
            if (docSchemas != null) {
                ApiDocDetailVo apiDocVo = new ApiDocDetailVo();
                SimpleModelUtils.copy(apiDoc, apiDocVo);
                processDocSchemas(docSchemas, apiDocVo);
                return apiDocVo;
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public boolean deleteByDoc(Integer docId) {
        return this.remove(Wrappers.<ApiDocSchema>query().eq("doc_id", docId));
    }
}
