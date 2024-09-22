package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProjectSchema;
import com.fugary.simple.api.mapper.api.ApiProjectSchemaMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectSchemaService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Created on 2020/5/3 22:37 .<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectSchemaServiceImpl extends ServiceImpl<ApiProjectSchemaMapper, ApiProjectSchema> implements ApiProjectSchemaService {

    @Override
    public void saveCopySchemas(Map<String, List<ApiProjectSchema>> schemaMap, Integer oldRequestId, Integer oldDataId, Integer requestId, Integer dataId) {
        List<ApiProjectSchema> schemas = schemaMap.get(StringUtils.join(oldRequestId, "-", oldDataId));
        this.saveCopySchemas(schemas, requestId, dataId);
    }

    @Override
    public void saveCopySchemas(List<ApiProjectSchema> schemas, Integer requestId, Integer dataId) {
        if (schemas != null) {
            schemas.forEach(schema -> {
                schema.setId(null);
//                schema.setRequestId(requestId);
//                schema.setDataId(dataId);
            });
            this.saveOrUpdateBatch(schemas);
        }
    }

    @Override
    public List<ApiProjectSchema> querySchemas(Integer requestId, Integer dataId) {
        if (requestId != null) {
//            return this.lambdaQuery().eq(ApiProjectSchema::getRequestId, requestId)
//                    .eq(dataId != null, ApiProjectSchema::getDataId, dataId)
//                    .isNull(dataId == null, ApiProjectSchema::getDataId)
//                    .list();
        }
        return List.of();
    }
}
