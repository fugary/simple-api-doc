package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.mapper.api.ApiDocSchemaMapper;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
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
    public boolean deleteByDoc(Integer docId) {
        return this.remove(Wrappers.<ApiDocSchema>query().eq("doc_id", docId));
    }
}
