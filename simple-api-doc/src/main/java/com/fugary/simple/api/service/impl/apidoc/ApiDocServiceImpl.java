package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.contants.ApiDocConstants;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.entity.api.ApiDocSchema;
import com.fugary.simple.api.mapper.api.ApiDocMapper;
import com.fugary.simple.api.service.apidoc.ApiDocSchemaService;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiDocServiceImpl extends ServiceImpl<ApiDocMapper, ApiDoc> implements ApiDocService {

    @Autowired
    private ApiDocSchemaService apiDocSchemaService;

    @Override
    public List<ApiDoc> loadByProject(Integer projectId) {
        return this.list(Wrappers.<ApiDoc>query().eq("project_id", projectId));
    }

    @Override
    public List<ApiDoc> loadEnabledByProject(Integer projectId) {
        return this.list(Wrappers.<ApiDoc>query().eq("project_id", projectId)
                .eq(ApiDocConstants.STATUS_KEY, ApiDocConstants.STATUS_ENABLED));
    }

    @Override
    public boolean deleteByProject(Integer projectId) {
        //select * from T_API_DOC_SCHEMA where exists (select 1 from t_api_doc d where d.project_id=0 and d.id=t_api_doc_schema.doc_id)
        apiDocSchemaService.remove(Wrappers.<ApiDocSchema>query()
                .exists("select 1 from t_api_doc d where d.project_id = {0} and d.id = t_api_doc_schema.doc_id", projectId));
        return this.remove(Wrappers.<ApiDoc>query().eq("project_id", projectId));
    }

    @Override
    public boolean deleteByFolder(Integer folderId) {
        //select * from T_API_DOC_SCHEMA where exists (select 1 from t_api_doc d where d.folder_id=0 and d.id=t_api_doc_schema.doc_id)
        apiDocSchemaService.remove(Wrappers.<ApiDocSchema>query()
                .exists("select 1 from t_api_doc d where d.folder_id = {0} and d.id = t_api_doc_schema.doc_id", folderId));
        return this.remove(Wrappers.<ApiDoc>query().eq("folder_id", folderId));
    }

    @Override
    public boolean deleteDoc(Integer docId) {
        apiDocSchemaService.remove(Wrappers.<ApiDocSchema>query().eq("doc_id", docId));
        return this.removeById(docId);
    }
}
